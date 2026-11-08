CREATE TABLE payment_checkouts (
 id UUID PRIMARY KEY,
 purpose VARCHAR(40) NOT NULL CHECK (purpose IN ('CONTRACT_FUND','SECURITY_DEPOSIT','MAINTENANCE_FUND','SUBSCRIPTION','JOB_ADDON')),
 object_id UUID NOT NULL,
 owner_id UUID NOT NULL REFERENCES users(id),
 amount NUMERIC(18,2) NOT NULL CHECK (amount > 0 AND amount = trunc(amount)),
 environment VARCHAR(16) NOT NULL CHECK (environment IN ('LIVE','SANDBOX')),
 status VARCHAR(16) NOT NULL CHECK (status IN ('CREATING','PENDING','UNKNOWN','PAID','CANCELLED','EXPIRED','REVIEW')),
 checkout_url TEXT,
 transfer_content VARCHAR(32) NOT NULL UNIQUE,
 expires_at TIMESTAMPTZ NOT NULL,
 fulfillment_status VARCHAR(16) NOT NULL DEFAULT 'NONE' CHECK (fulfillment_status IN ('NONE','PENDING','RETRY','APPLIED','REVIEW')),
 attempt_count INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 transaction_reference VARCHAR(150),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 paid_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_payment_checkouts_open ON payment_checkouts(purpose, object_id, environment)
 WHERE status IN ('CREATING','PENDING','UNKNOWN','PAID','REVIEW');
CREATE INDEX ix_payment_checkouts_owner ON payment_checkouts(owner_id, created_at DESC);
CREATE INDEX ix_payment_checkouts_fulfillment ON payment_checkouts(next_attempt_at) WHERE fulfillment_status IN ('PENDING','RETRY');
CREATE TABLE payment_checkout_receipts (
 id UUID PRIMARY KEY,
 checkout_id UUID NOT NULL REFERENCES payment_checkouts(id),
 environment VARCHAR(16) NOT NULL,
 provider_reference VARCHAR(150) NOT NULL,
 amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
 disposition VARCHAR(16) NOT NULL CHECK (disposition IN ('ACCEPTED','REVIEW')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE (environment, provider_reference)
);
ALTER TABLE user_subscriptions ADD COLUMN environment VARCHAR(16) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE user_subscriptions ADD COLUMN amount_paid NUMERIC(18,2);
ALTER TABLE user_subscriptions ADD CONSTRAINT ck_subscription_environment CHECK (environment IN ('LEGACY','LIVE','SANDBOX'));
-- Existing provider references and paid history are retained. Do not relabel historical money.

ALTER TABLE payments ALTER COLUMN provider SET DEFAULT 'PAY2S';

CREATE TRIGGER payment_checkout_receipts_immutable BEFORE UPDATE OR DELETE ON payment_checkout_receipts
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();
CREATE FUNCTION payment_checkout_identity_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF (OLD.id,OLD.purpose,OLD.object_id,OLD.owner_id,OLD.amount,OLD.environment,OLD.transfer_content,OLD.created_at,OLD.expires_at)
 IS DISTINCT FROM (NEW.id,NEW.purpose,NEW.object_id,NEW.owner_id,NEW.amount,NEW.environment,NEW.transfer_content,NEW.created_at,NEW.expires_at)
 THEN RAISE EXCEPTION 'Checkout identity is immutable'; END IF;
 IF OLD.status='PAID' AND NEW.status<>'PAID' THEN RAISE EXCEPTION 'Paid checkout cannot be downgraded'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER payment_checkout_identity BEFORE UPDATE ON payment_checkouts FOR EACH ROW EXECUTE FUNCTION payment_checkout_identity_guard();

CREATE TABLE job_addon_tasks (
 job_id UUID PRIMARY KEY REFERENCES jobs(id),
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RETRY','DONE')),
 attempt_count INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_job_addon_tasks_pending ON job_addon_tasks(next_attempt_at) WHERE status IN ('PENDING','RETRY');
