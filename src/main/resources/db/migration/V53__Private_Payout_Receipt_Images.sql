CREATE TABLE finance_payout_receipt_images (
 id UUID PRIMARY KEY,
 withdrawal_id UUID NOT NULL REFERENCES finance_withdrawals(id),
 uploaded_by UUID NOT NULL REFERENCES users(id),
 file_name VARCHAR(200) NOT NULL,
 content_type VARCHAR(20) NOT NULL CHECK(content_type IN ('image/jpeg','image/png','image/webp')),
 size_bytes INTEGER NOT NULL CHECK(size_bytes BETWEEN 1 AND 5242880),
 sha256 VARCHAR(64) NOT NULL,
 content_encrypted TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_payout_receipt_withdrawal ON finance_payout_receipt_images(withdrawal_id,id);
CREATE TRIGGER finance_payout_receipts_immutable BEFORE UPDATE OR DELETE ON finance_payout_receipt_images
 FOR EACH ROW EXECUTE FUNCTION finance_immutable();

CREATE FUNCTION finance_payout_receipt_writer() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM finance_withdrawals WHERE id=NEW.withdrawal_id
   AND status IN ('PROCESSING','RECONCILIATION_REQUIRED') AND operator_id=NEW.uploaded_by)
 THEN RAISE EXCEPTION 'Receipt must belong to the operator of an active transfer'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER finance_payout_receipt_writer BEFORE INSERT ON finance_payout_receipt_images
 FOR EACH ROW EXECUTE FUNCTION finance_payout_receipt_writer();

CREATE FUNCTION finance_assert_payout_receipt() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE w finance_withdrawals; image_count BIGINT;
BEGIN
 SELECT * INTO w FROM finance_withdrawals WHERE id=NEW.withdrawal_id;
 SELECT COUNT(*) INTO image_count FROM finance_payout_receipt_images WHERE withdrawal_id=w.id;
 IF w.status<>'PAID' OR w.evidence_reference IS DISTINCT FROM 'CT-'||UPPER(w.id::text)
   OR image_count NOT BETWEEN 1 AND 5
 THEN RAISE EXCEPTION 'Receipt images must be finalized with a paid transfer'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER finance_payout_receipt_finalized AFTER INSERT ON finance_payout_receipt_images
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION finance_assert_payout_receipt();

CREATE FUNCTION finance_assert_payout_receipt_reference() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE w finance_withdrawals; image_count BIGINT;
BEGIN
 SELECT * INTO w FROM finance_withdrawals WHERE id=NEW.id;
 IF w.evidence_reference='CT-'||UPPER(w.id::text) THEN
   SELECT COUNT(*) INTO image_count FROM finance_payout_receipt_images WHERE withdrawal_id=w.id;
   IF w.status<>'PAID' OR image_count NOT BETWEEN 1 AND 5
   THEN RAISE EXCEPTION 'An uploaded receipt reference requires receipt images'; END IF;
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER finance_payout_receipt_reference AFTER INSERT OR UPDATE ON finance_withdrawals
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION finance_assert_payout_receipt_reference();
