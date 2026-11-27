ALTER TABLE finance_withdrawals
 ADD COLUMN request_type VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
 ADD COLUMN early_payable_id UUID REFERENCES finance_payables(id),
 ADD COLUMN request_reason VARCHAR(2000),
 ADD COLUMN writer_transaction BIGINT NOT NULL DEFAULT txid_current(),
 ADD CONSTRAINT ck_withdrawal_request_type CHECK (
   (request_type='NORMAL' AND early_payable_id IS NULL AND request_reason IS NULL) OR
   (request_type='EARLY' AND early_payable_id IS NOT NULL AND LENGTH(BTRIM(request_reason)) BETWEEN 1 AND 2000 AND request_reason IS NOT NULL));

CREATE UNIQUE INDEX uk_active_early_withdrawal ON finance_withdrawals(early_payable_id)
 WHERE request_type='EARLY' AND status IN ('REQUESTED','APPROVED','PROCESSING','RECONCILIATION_REQUIRED');
CREATE INDEX ix_withdrawal_type_created ON finance_withdrawals(request_type,created_at DESC,id);

CREATE TABLE finance_withdrawal_images (
 id UUID PRIMARY KEY,
 withdrawal_id UUID NOT NULL REFERENCES finance_withdrawals(id),
 file_name VARCHAR(200) NOT NULL,
 content_type VARCHAR(20) NOT NULL CHECK(content_type IN ('image/jpeg','image/png','image/webp')),
 size_bytes INTEGER NOT NULL CHECK(size_bytes BETWEEN 1 AND 5242880),
 sha256 VARCHAR(64) NOT NULL,
 content_encrypted TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_withdrawal_image_parent ON finance_withdrawal_images(withdrawal_id,id);
CREATE TRIGGER finance_withdrawal_images_immutable BEFORE UPDATE OR DELETE ON finance_withdrawal_images
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();

CREATE FUNCTION finance_early_terms_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.request_type,NEW.early_payable_id,NEW.request_reason,NEW.writer_transaction)
 IS DISTINCT FROM ROW(OLD.request_type,OLD.early_payable_id,OLD.request_reason,OLD.writer_transaction)
 THEN RAISE EXCEPTION 'Early withdrawal terms are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER finance_early_terms BEFORE UPDATE ON finance_withdrawals
 FOR EACH ROW EXECUTE FUNCTION finance_early_terms_immutable();

CREATE FUNCTION finance_withdrawal_image_writer() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM finance_withdrawals WHERE id=NEW.withdrawal_id AND request_type='EARLY' AND writer_transaction=txid_current())
 THEN RAISE EXCEPTION 'Evidence can only be attached when creating an early withdrawal'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER finance_withdrawal_image_writer BEFORE INSERT ON finance_withdrawal_images
 FOR EACH ROW EXECUTE FUNCTION finance_withdrawal_image_writer();

CREATE FUNCTION finance_assert_early_withdrawal() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE p finance_payables; image_count BIGINT; allocation_count BIGINT; allocated NUMERIC;
BEGIN
 IF NEW.request_type<>'EARLY' THEN RETURN NULL; END IF;
 SELECT * INTO p FROM finance_payables WHERE id=NEW.early_payable_id;
 SELECT COUNT(*) INTO image_count FROM finance_withdrawal_images WHERE withdrawal_id=NEW.id;
 SELECT COUNT(*),COALESCE(SUM(amount),0) INTO allocation_count,allocated
 FROM finance_withdrawal_allocations WHERE withdrawal_id=NEW.id AND payable_id=p.id;
 IF p.owner_id<>NEW.owner_id OR p.sandbox OR p.maintenance_order_id IS NOT NULL
 OR NEW.created_at<p.accepted_at OR NEW.created_at>=p.available_at
 OR image_count NOT BETWEEN 1 AND 5 OR allocation_count<>1 OR allocated<>NEW.amount
 OR (SELECT COUNT(*) FROM finance_withdrawal_allocations WHERE withdrawal_id=NEW.id)<>1
 THEN RAISE EXCEPTION 'Invalid early withdrawal source, evidence or allocation'; END IF;
 IF p.reserved+p.paid+p.adjusted>p.earnings
 THEN RAISE EXCEPTION 'Early withdrawals must retain the security deposit'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER finance_early_withdrawal_check AFTER INSERT ON finance_withdrawals
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION finance_assert_early_withdrawal();
