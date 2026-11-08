ALTER TABLE payments ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT 'VNPAY';
ALTER TABLE payments ADD COLUMN environment VARCHAR(16) NOT NULL DEFAULT 'SANDBOX';
ALTER TABLE payments ADD COLUMN verified_at TIMESTAMPTZ;
ALTER TABLE payments ADD COLUMN checkout_url TEXT;
ALTER TABLE payments ADD COLUMN provider_transaction_id VARCHAR(150);
CREATE UNIQUE INDEX uk_payment_provider_transaction ON payments(provider, environment, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;
CREATE UNIQUE INDEX uk_payment_gateway_ref ON payments(payment_gateway_id) WHERE payment_gateway_id IS NOT NULL;

CREATE TABLE finance_payables (
 id UUID PRIMARY KEY, settlement_id UUID NOT NULL UNIQUE REFERENCES project_settlements(id),
 contract_id UUID NOT NULL UNIQUE REFERENCES contracts(id), job_id UUID NOT NULL REFERENCES jobs(id),
 owner_id UUID NOT NULL REFERENCES users(id), client_id UUID NOT NULL REFERENCES users(id),
 amount NUMERIC(38,2) NOT NULL CHECK(amount>0), earnings NUMERIC(38,2) NOT NULL CHECK(earnings>=0),
 deposit_refund NUMERIC(38,2) NOT NULL CHECK(deposit_refund>=0),
 reserved NUMERIC(38,2) NOT NULL DEFAULT 0 CHECK(reserved>=0),
 paid NUMERIC(38,2) NOT NULL DEFAULT 0 CHECK(paid>=0),
 adjusted NUMERIC(38,2) NOT NULL DEFAULT 0 CHECK(adjusted>=0),
 sandbox BOOLEAN NOT NULL, accepted_at TIMESTAMPTZ NOT NULL, available_at TIMESTAMPTZ NOT NULL,
 policy_version VARCHAR(40) NOT NULL DEFAULT 'maintenance-7d-v1',
 CHECK(amount=earnings+deposit_refund), CHECK(reserved+paid+adjusted<=amount),
 CHECK(available_at>=accepted_at+INTERVAL '168 hours')
);
CREATE INDEX ix_finance_payables_owner ON finance_payables(owner_id,available_at,id);
CREATE TABLE maintenance_tickets (
 id UUID PRIMARY KEY, payable_id UUID NOT NULL REFERENCES finance_payables(id),
 creator_id UUID NOT NULL REFERENCES users(id), request_key UUID NOT NULL,
 kind VARCHAR(12) NOT NULL CHECK(kind IN ('WARRANTY','PAID')),
 title VARCHAR(200) NOT NULL, description TEXT NOT NULL,
 status VARCHAR(30) NOT NULL CHECK(status IN ('OPEN','IN_PROGRESS','WAITING_CLIENT','FIX_SUBMITTED','DISPUTED','RESOLVED','OUT_OF_SCOPE','WITHDRAWN')),
 created_at TIMESTAMPTZ NOT NULL, response_due_at TIMESTAMPTZ NOT NULL,
 resolution_due_at TIMESTAMPTZ NOT NULL, fix_submitted_at TIMESTAMPTZ,
 overdue_at TIMESTAMPTZ, closed_at TIMESTAMPTZ,
 UNIQUE(creator_id,request_key)
);
CREATE INDEX ix_ticket_payable_state ON maintenance_tickets(payable_id,kind,status);
CREATE INDEX ix_ticket_overdue ON maintenance_tickets(resolution_due_at) WHERE closed_at IS NULL;
CREATE TABLE maintenance_ticket_events (
 id UUID PRIMARY KEY, ticket_id UUID NOT NULL REFERENCES maintenance_tickets(id),
 actor_id UUID REFERENCES users(id), action VARCHAR(40) NOT NULL, content TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_ticket_event_time ON maintenance_ticket_events(ticket_id,created_at,id);
CREATE TABLE finance_bank_accounts (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), bank_code VARCHAR(20) NOT NULL,
 details_encrypted TEXT NOT NULL, last_four VARCHAR(4) NOT NULL,
 fingerprint VARCHAR(64) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 verification_note VARCHAR(1000), verified_by UUID REFERENCES users(id), verified_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL,
 CHECK(status IN ('PENDING','VERIFIED','REJECTED','DISABLED'))
);
CREATE UNIQUE INDEX uk_finance_active_bank ON finance_bank_accounts(owner_id,fingerprint) WHERE status IN ('PENDING','VERIFIED');
CREATE INDEX ix_finance_bank_owner ON finance_bank_accounts(owner_id,created_at);
CREATE TABLE finance_withdrawals (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), request_key UUID NOT NULL,
 bank_account_id UUID NOT NULL REFERENCES finance_bank_accounts(id),
 beneficiary_encrypted TEXT NOT NULL, bank_code VARCHAR(20) NOT NULL, last_four VARCHAR(4) NOT NULL,
 amount NUMERIC(38,2) NOT NULL CHECK(amount>0),
 status VARCHAR(30) NOT NULL CHECK(status IN ('REQUESTED','APPROVED','PROCESSING','RECONCILIATION_REQUIRED','PAID','REJECTED','CANCELLED','FAILED')),
 approved_by UUID REFERENCES users(id), operator_id UUID REFERENCES users(id),
 bank_reference VARCHAR(150) UNIQUE, evidence_reference VARCHAR(300),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, UNIQUE(owner_id,request_key)
);
CREATE INDEX ix_finance_withdrawal_owner ON finance_withdrawals(owner_id,created_at DESC,id);
CREATE TABLE finance_withdrawal_allocations (
 withdrawal_id UUID NOT NULL REFERENCES finance_withdrawals(id),
 payable_id UUID NOT NULL REFERENCES finance_payables(id), amount NUMERIC(38,2) NOT NULL CHECK(amount>0),
 PRIMARY KEY(withdrawal_id,payable_id)
);
CREATE TABLE finance_audit_events (
 id UUID PRIMARY KEY, actor_id UUID REFERENCES users(id), owner_id UUID REFERENCES users(id),
 subject_id UUID NOT NULL, action VARCHAR(60) NOT NULL, detail VARCHAR(2000) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_finance_audit_subject ON finance_audit_events(subject_id,created_at,id);
CREATE TABLE finance_journals (
 id UUID PRIMARY KEY, event_key VARCHAR(160) NOT NULL UNIQUE, contract_id UUID REFERENCES contracts(id),
 owner_id UUID REFERENCES users(id), description VARCHAR(500) NOT NULL, sandbox BOOLEAN NOT NULL,
 created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE finance_entries (
 id UUID PRIMARY KEY, journal_id UUID NOT NULL REFERENCES finance_journals(id),
 account_key VARCHAR(160) NOT NULL, debit NUMERIC(38,2) NOT NULL DEFAULT 0,
 credit NUMERIC(38,2) NOT NULL DEFAULT 0,
 CHECK((debit>0 AND credit=0) OR (credit>0 AND debit=0))
);
CREATE INDEX ix_finance_journal_owner ON finance_journals(owner_id,created_at DESC,id);
CREATE INDEX ix_finance_entry_journal ON finance_entries(journal_id);
CREATE FUNCTION finance_assert_balanced() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE target UUID; line_count BIGINT; delta NUMERIC;
BEGIN
 target := NEW.id;
 SELECT COUNT(*), COALESCE(SUM(debit-credit),0) INTO line_count,delta
 FROM finance_entries WHERE journal_id=target;
 IF line_count<2 OR delta<>0 THEN RAISE EXCEPTION 'Unbalanced finance journal'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER finance_journal_balanced AFTER INSERT ON finance_journals
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION finance_assert_balanced();
CREATE FUNCTION finance_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Financial history is append-only'; END $$;
CREATE TRIGGER finance_entries_immutable BEFORE UPDATE OR DELETE ON finance_entries
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();
CREATE TRIGGER finance_journals_immutable BEFORE UPDATE OR DELETE ON finance_journals
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();
CREATE TRIGGER finance_audit_immutable BEFORE UPDATE OR DELETE ON finance_audit_events
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();
CREATE TRIGGER maintenance_events_immutable BEFORE UPDATE OR DELETE ON maintenance_ticket_events
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();

CREATE TABLE finance_receipts (
 id UUID PRIMARY KEY, payment_id UUID NOT NULL REFERENCES payments(id), provider VARCHAR(16) NOT NULL,
 environment VARCHAR(16) NOT NULL, provider_reference VARCHAR(150) NOT NULL,
 amount NUMERIC(38,2) NOT NULL CHECK(amount>0), disposition VARCHAR(12) NOT NULL CHECK(disposition IN ('APPLIED','REVIEW')),
 created_at TIMESTAMPTZ NOT NULL, UNIQUE(provider,environment,provider_reference)
);
CREATE TRIGGER finance_receipts_immutable BEFORE UPDATE OR DELETE ON finance_receipts
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();
ALTER TABLE finance_journals ADD COLUMN writer_transaction BIGINT NOT NULL DEFAULT txid_current();
CREATE FUNCTION finance_restrict_new_entry() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM finance_journals WHERE id=NEW.journal_id AND writer_transaction=txid_current())
 THEN RAISE EXCEPTION 'Cannot append to committed finance journal'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER finance_entry_writer BEFORE INSERT ON finance_entries
 FOR EACH ROW EXECUTE FUNCTION finance_restrict_new_entry();

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check CHECK(status IN ('PENDING','HELD_IN_ESCROW','RELEASED','REFUNDED','EXPIRED','SETTLED'));
CREATE TABLE maintenance_quotes (
 id UUID PRIMARY KEY,ticket_id UUID NOT NULL REFERENCES maintenance_tickets(id),
 amount NUMERIC(38,2) NOT NULL CHECK(amount>0 AND amount=TRUNC(amount)),
 delivery_days INTEGER NOT NULL CHECK(delivery_days BETWEEN 1 AND 90),
 scope TEXT NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'QUOTED',
 created_at TIMESTAMPTZ NOT NULL,CHECK(status IN ('QUOTED','ACCEPTED','DECLINED','SUPERSEDED'))
);
CREATE INDEX ix_maintenance_quote_ticket ON maintenance_quotes(ticket_id,created_at DESC,id);
CREATE TABLE paid_maintenance_orders (
 id UUID PRIMARY KEY,quote_id UUID NOT NULL UNIQUE REFERENCES maintenance_quotes(id),
 ticket_id UUID NOT NULL UNIQUE REFERENCES maintenance_tickets(id),
 owner_id UUID NOT NULL REFERENCES users(id),client_id UUID NOT NULL REFERENCES users(id),
 contract_id UUID NOT NULL REFERENCES contracts(id),job_id UUID NOT NULL REFERENCES jobs(id),
 payment_id UUID UNIQUE REFERENCES payments(id),amount NUMERIC(38,2) NOT NULL CHECK(amount>0),
 delivery_days INTEGER NOT NULL CHECK(delivery_days BETWEEN 1 AND 90),scope TEXT NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'AWAITING_PAYMENT',
 created_at TIMESTAMPTZ NOT NULL,funded_at TIMESTAMPTZ,due_at TIMESTAMPTZ,
 submitted_at TIMESTAMPTZ,completed_at TIMESTAMPTZ,
 CHECK(status IN ('AWAITING_PAYMENT','FUNDED','IN_PROGRESS','SUBMITTED','COMPLETED','DISPUTED'))
);
ALTER TABLE payments ADD COLUMN maintenance_order_id UUID REFERENCES paid_maintenance_orders(id);
ALTER TABLE finance_payables ALTER COLUMN settlement_id DROP NOT NULL;
ALTER TABLE finance_payables DROP CONSTRAINT finance_payables_contract_id_key;
ALTER TABLE finance_payables ADD COLUMN maintenance_order_id UUID UNIQUE REFERENCES paid_maintenance_orders(id);
CREATE UNIQUE INDEX uk_finance_original_contract ON finance_payables(contract_id) WHERE maintenance_order_id IS NULL;
ALTER TABLE finance_payables ADD CONSTRAINT ck_payable_source CHECK((settlement_id IS NOT NULL AND maintenance_order_id IS NULL) OR (settlement_id IS NULL AND maintenance_order_id IS NOT NULL));
CREATE TABLE paid_maintenance_order_events (
 id UUID PRIMARY KEY,order_id UUID NOT NULL REFERENCES paid_maintenance_orders(id),actor_id UUID NOT NULL REFERENCES users(id),
 action VARCHAR(30) NOT NULL,content TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL
);
CREATE TRIGGER paid_maintenance_events_immutable BEFORE UPDATE OR DELETE ON paid_maintenance_order_events
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();

CREATE FUNCTION finance_payable_source_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.settlement_id,NEW.contract_id,NEW.job_id,NEW.owner_id,NEW.client_id,NEW.amount,NEW.earnings,NEW.deposit_refund,NEW.sandbox,NEW.accepted_at,NEW.available_at,NEW.policy_version,NEW.maintenance_order_id)
 IS DISTINCT FROM ROW(OLD.settlement_id,OLD.contract_id,OLD.job_id,OLD.owner_id,OLD.client_id,OLD.amount,OLD.earnings,OLD.deposit_refund,OLD.sandbox,OLD.accepted_at,OLD.available_at,OLD.policy_version,OLD.maintenance_order_id)
 THEN RAISE EXCEPTION 'Payable source is immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER finance_payable_identity BEFORE UPDATE ON finance_payables
 FOR EACH ROW EXECUTE FUNCTION finance_payable_source_immutable();
CREATE TRIGGER finance_allocations_immutable BEFORE UPDATE OR DELETE ON finance_withdrawal_allocations
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();
CREATE FUNCTION finance_withdrawal_identity_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.owner_id,NEW.request_key,NEW.bank_account_id,NEW.beneficiary_encrypted,NEW.bank_code,NEW.last_four,NEW.amount,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.owner_id,OLD.request_key,OLD.bank_account_id,OLD.beneficiary_encrypted,OLD.bank_code,OLD.last_four,OLD.amount,OLD.created_at)
 THEN RAISE EXCEPTION 'Withdrawal beneficiary and amount are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER finance_withdrawal_identity BEFORE UPDATE ON finance_withdrawals
 FOR EACH ROW EXECUTE FUNCTION finance_withdrawal_identity_immutable();
CREATE FUNCTION finance_payable_allocations_match() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE target UUID; held NUMERIC; sent NUMERIC; p finance_payables;
BEGIN
 target := NEW.id;
 SELECT * INTO p FROM finance_payables WHERE id=target;
 SELECT COALESCE(SUM(a.amount) FILTER(WHERE w.status IN ('REQUESTED','APPROVED','PROCESSING','RECONCILIATION_REQUIRED')),0),
        COALESCE(SUM(a.amount) FILTER(WHERE w.status='PAID'),0)
 INTO held,sent FROM finance_withdrawal_allocations a JOIN finance_withdrawals w ON w.id=a.withdrawal_id WHERE a.payable_id=target;
 IF p.reserved<>held OR p.paid<>sent THEN RAISE EXCEPTION 'Payable allocations do not match'; END IF;
 IF EXISTS(SELECT 1 FROM finance_withdrawal_allocations a JOIN finance_withdrawals w ON w.id=a.withdrawal_id WHERE a.payable_id=target AND w.owner_id<>p.owner_id)
 THEN RAISE EXCEPTION 'Allocation owner mismatch'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER finance_payable_allocation_check AFTER INSERT OR UPDATE ON finance_payables
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION finance_payable_allocations_match();

CREATE INDEX ix_paid_maintenance_events_order ON paid_maintenance_order_events(order_id,created_at DESC,id DESC);

ALTER TABLE maintenance_tickets ADD CONSTRAINT ck_ticket_terminal_date CHECK((closed_at IS NOT NULL)=(status IN ('RESOLVED','OUT_OF_SCOPE','WITHDRAWN')));
ALTER TABLE maintenance_tickets ADD CONSTRAINT ck_ticket_deadlines CHECK(response_due_at>=created_at AND resolution_due_at>=created_at AND (closed_at IS NULL OR closed_at>=created_at));
CREATE FUNCTION maintenance_ticket_window_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE p finance_payables;
BEGIN
 SELECT * INTO p FROM finance_payables WHERE id=NEW.payable_id;
 IF NEW.creator_id<>p.client_id THEN RAISE EXCEPTION 'Ticket creator is not the project client'; END IF;
 IF NEW.kind='WARRANTY' AND (NEW.created_at<p.accepted_at OR NEW.created_at>=p.available_at)
 THEN RAISE EXCEPTION 'Warranty window expired'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER maintenance_ticket_window BEFORE INSERT ON maintenance_tickets
 FOR EACH ROW EXECUTE FUNCTION maintenance_ticket_window_guard();
CREATE FUNCTION maintenance_quote_identity_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.ticket_id,NEW.amount,NEW.delivery_days,NEW.scope,NEW.created_at)
 IS DISTINCT FROM ROW(OLD.ticket_id,OLD.amount,OLD.delivery_days,OLD.scope,OLD.created_at)
 THEN RAISE EXCEPTION 'Quote terms are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER maintenance_quote_terms BEFORE UPDATE ON maintenance_quotes
 FOR EACH ROW EXECUTE FUNCTION maintenance_quote_identity_guard();
CREATE INDEX ix_finance_original_job ON finance_payables(job_id) WHERE maintenance_order_id IS NULL;
CREATE INDEX ix_maintenance_blocking_payable ON maintenance_tickets(payable_id) WHERE kind='WARRANTY' AND closed_at IS NULL;
CREATE INDEX ix_maintenance_first_response ON maintenance_tickets(response_due_at) WHERE kind='WARRANTY' AND closed_at IS NULL AND overdue_at IS NULL;
CREATE INDEX ix_maintenance_responder ON maintenance_ticket_events(ticket_id,actor_id);
CREATE INDEX ix_finance_audit_recent ON finance_audit_events(created_at DESC,id DESC);
CREATE INDEX ix_finance_receipts_recent ON finance_receipts(created_at DESC,id DESC);
CREATE INDEX ix_finance_withdrawals_recent ON finance_withdrawals(created_at DESC,id DESC);
CREATE INDEX ix_finance_banks_recent ON finance_bank_accounts(created_at DESC,id DESC);
