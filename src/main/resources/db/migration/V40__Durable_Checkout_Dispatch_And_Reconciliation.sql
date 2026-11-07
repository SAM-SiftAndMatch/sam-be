-- Never replay historical CREATING/UNKNOWN orders: they may already exist at Pay2S.
ALTER TABLE payment_checkouts ADD COLUMN support_code VARCHAR(35);
UPDATE payment_checkouts SET support_code='SAM'||upper(replace(id::text,'-',''));
ALTER TABLE payment_checkouts ALTER COLUMN support_code SET NOT NULL;
ALTER TABLE payment_checkouts ALTER COLUMN support_code SET DEFAULT ('SAM'||upper(replace(gen_random_uuid()::text,'-','')));
CREATE UNIQUE INDEX uq_checkout_support_code ON payment_checkouts(support_code);
ALTER TABLE payment_checkouts ADD COLUMN merchant_scope VARCHAR(64);
ALTER TABLE payment_checkouts ADD COLUMN replacement_allowed BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE payment_checkouts ADD COLUMN provider_transfer_content VARCHAR(32);

CREATE TABLE payment_creation_tasks (
 checkout_id UUID PRIMARY KEY REFERENCES payment_checkouts(id),
 return_url TEXT NOT NULL,
 state VARCHAR(16) NOT NULL CHECK(state IN ('READY','LEASED','DONE','UNKNOWN','REJECTED')),
 locked_by UUID,
 locked_until TIMESTAMPTZ,
 generation BIGINT NOT NULL DEFAULT 0,
 dispatched_at TIMESTAMPTZ,
 error_code VARCHAR(80),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX ix_payment_creation_ready ON payment_creation_tasks(updated_at) WHERE state IN ('READY','LEASED');
CREATE TABLE payment_creation_observations (
 id UUID PRIMARY KEY,
 checkout_id UUID NOT NULL REFERENCES payment_checkouts(id),
 generation BIGINT NOT NULL,
 checkout_url TEXT NOT NULL,
 transfer_content VARCHAR(32),
 received_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER payment_creation_observations_immutable BEFORE UPDATE OR DELETE ON payment_creation_observations
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();

CREATE TABLE payment_idempotency_keys (
 owner_id UUID NOT NULL REFERENCES users(id),
 environment VARCHAR(16) NOT NULL,
 purpose VARCHAR(40) NOT NULL,
 key_hash VARCHAR(64) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 checkout_id UUID NOT NULL REFERENCES payment_checkouts(id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 PRIMARY KEY(owner_id,environment,purpose,key_hash)
);
CREATE TRIGGER payment_idempotency_immutable BEFORE UPDATE OR DELETE ON payment_idempotency_keys
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();

CREATE TABLE payment_provider_events (
 id UUID PRIMARY KEY,
 environment VARCHAR(16) NOT NULL CHECK(environment IN ('LIVE','SANDBOX')),
 merchant_scope VARCHAR(64) NOT NULL,
 event_hash VARCHAR(64) NOT NULL,
 verified_payload JSONB NOT NULL,
 order_reference VARCHAR(80) NOT NULL,
 result_code INTEGER NOT NULL,
 amount_text VARCHAR(80) NOT NULL,
 provider_reference VARCHAR(150) NOT NULL,
 transfer_content VARCHAR(32) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','APPLIED','REVIEW')),
 attempt_count INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 error_code VARCHAR(80),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 processed_at TIMESTAMPTZ,
 UNIQUE(environment,merchant_scope,event_hash)
);
CREATE INDEX ix_payment_events_pending ON payment_provider_events(next_attempt_at) WHERE state='PENDING';

CREATE TABLE payment_reconciliation_cases (
 id UUID PRIMARY KEY,
 case_key VARCHAR(200) NOT NULL UNIQUE,
 checkout_id UUID REFERENCES payment_checkouts(id),
 event_id UUID REFERENCES payment_provider_events(id),
 reason VARCHAR(80) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK(state IN ('OPEN','INVESTIGATING','RESOLVED')),
 assignee_id UUID REFERENCES users(id),
 due_at TIMESTAMPTZ NOT NULL DEFAULT (clock_timestamp()+INTERVAL '4 hours'),
 resolution VARCHAR(80),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 resolved_at TIMESTAMPTZ
);
CREATE INDEX ix_payment_cases_open ON payment_reconciliation_cases(due_at) WHERE state<>'RESOLVED';
CREATE TABLE payment_reconciliation_events (
 id UUID PRIMARY KEY,
 case_id UUID NOT NULL REFERENCES payment_reconciliation_cases(id),
 actor_id UUID REFERENCES users(id),
 action VARCHAR(80) NOT NULL,
 evidence_reference VARCHAR(500),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER payment_case_events_immutable BEFORE UPDATE OR DELETE ON payment_reconciliation_events
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();
CREATE FUNCTION payment_case_state_audit() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF (OLD.state,OLD.assignee_id,OLD.resolution) IS DISTINCT FROM (NEW.state,NEW.assignee_id,NEW.resolution) THEN
  INSERT INTO payment_reconciliation_events(id,case_id,action,evidence_reference)
  VALUES(gen_random_uuid(),NEW.id,'STATE_CHANGED',OLD.state||' -> '||NEW.state||COALESCE(': '||NEW.resolution,''));
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payment_case_state_changed AFTER UPDATE ON payment_reconciliation_cases
 FOR EACH ROW EXECUTE FUNCTION payment_case_state_audit();

INSERT INTO payment_reconciliation_cases(id,case_key,checkout_id,reason)
 SELECT id,'checkout:'||id||':LEGACY_UNCERTAIN',id,'LEGACY_UNCERTAIN'
 FROM payment_checkouts WHERE status IN ('CREATING','UNKNOWN','REVIEW');

-- Preserve historical subscriptions, including duplicate LEGACY pending rows.
-- Only the supported Pay2S environments participate in the new purchase constraint.
-- Genuine LIVE/SANDBOX duplicates still require review; never delete/relabel financial history.
CREATE UNIQUE INDEX uq_pending_recurring_subscription ON user_subscriptions(user_id,package_id,environment)
 WHERE status='PENDING' AND environment IN ('LIVE','SANDBOX')
 AND package_id IN ('f0000000-0000-0000-0000-000000000005','f0000000-0000-0000-0000-000000000006');
CREATE UNIQUE INDEX uq_pending_pay2s_contract_obligation ON payments(contract_id,payment_type,environment)
 WHERE provider='PAY2S' AND status='PENDING' AND maintenance_order_id IS NULL;
CREATE UNIQUE INDEX uq_pending_pay2s_maintenance_obligation ON payments(maintenance_order_id,environment)
 WHERE provider='PAY2S' AND status='PENDING' AND maintenance_order_id IS NOT NULL;

CREATE OR REPLACE FUNCTION payment_checkout_identity_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF (OLD.id,OLD.purpose,OLD.object_id,OLD.owner_id,OLD.amount,OLD.environment,OLD.transfer_content,OLD.created_at,OLD.expires_at,OLD.support_code,OLD.merchant_scope)
 IS DISTINCT FROM (NEW.id,NEW.purpose,NEW.object_id,NEW.owner_id,NEW.amount,NEW.environment,NEW.transfer_content,NEW.created_at,NEW.expires_at,NEW.support_code,NEW.merchant_scope)
 THEN RAISE EXCEPTION 'Checkout identity is immutable'; END IF;
 IF OLD.provider_transfer_content IS NOT NULL AND OLD.provider_transfer_content IS DISTINCT FROM NEW.provider_transfer_content
 THEN RAISE EXCEPTION 'Provider reference is immutable'; END IF;
 IF OLD.status<>NEW.status AND NOT (
  (OLD.status='CREATING' AND NEW.status IN ('PENDING','UNKNOWN','PAID','CANCELLED','EXPIRED','REVIEW')) OR
  (OLD.status='PENDING' AND NEW.status IN ('UNKNOWN','PAID','CANCELLED','EXPIRED','REVIEW')) OR
  (OLD.status='UNKNOWN' AND NEW.status IN ('PENDING','PAID','CANCELLED','REVIEW')) OR
  (OLD.status IN ('EXPIRED','CANCELLED') AND NEW.status IN ('REVIEW','PAID')) OR
  (OLD.status='REVIEW' AND NEW.status='PAID')
 ) THEN RAISE EXCEPTION 'Illegal checkout transition'; END IF;
 IF OLD.fulfillment_status='APPLIED' AND NEW.fulfillment_status<>'APPLIED'
 THEN RAISE EXCEPTION 'Applied checkout cannot be downgraded'; END IF;
 IF NEW.fulfillment_status IN ('PENDING','RETRY','APPLIED') AND NEW.status<>'PAID'
 THEN RAISE EXCEPTION 'Fulfillment requires verified payment'; END IF;
 RETURN NEW;
END $$;

CREATE FUNCTION payment_event_identity_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF (OLD.id,OLD.environment,OLD.merchant_scope,OLD.event_hash,OLD.verified_payload,OLD.order_reference,OLD.result_code,OLD.amount_text,OLD.provider_reference,OLD.transfer_content,OLD.created_at)
 IS DISTINCT FROM (NEW.id,NEW.environment,NEW.merchant_scope,NEW.event_hash,NEW.verified_payload,NEW.order_reference,NEW.result_code,NEW.amount_text,NEW.provider_reference,NEW.transfer_content,NEW.created_at)
 THEN RAISE EXCEPTION 'Verified provider evidence is immutable'; END IF;
 IF OLD.state='APPLIED' AND NEW.state<>'APPLIED' THEN RAISE EXCEPTION 'Applied event cannot be downgraded'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payment_event_identity BEFORE UPDATE ON payment_provider_events FOR EACH ROW EXECUTE FUNCTION payment_event_identity_guard();
CREATE TRIGGER payment_event_no_delete BEFORE DELETE ON payment_provider_events FOR EACH ROW EXECUTE FUNCTION finance_immutable();
