-- Expiry closes SAM's payment window, but the provider order may still be pending.
-- After provider cancellation is confirmed, retain the expired checkout as CANCELLED.
-- Replacement permission/evidence remains the responsibility of the existing services.
-- Preserve V40's immutable identity, terminal payment and fulfillment protections.
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
  (OLD.status='EXPIRED' AND NEW.status='CANCELLED') OR
  (OLD.status IN ('EXPIRED','CANCELLED') AND NEW.status IN ('REVIEW','PAID')) OR
  (OLD.status='REVIEW' AND NEW.status='PAID')
 ) THEN RAISE EXCEPTION 'Illegal checkout transition'; END IF;
 IF OLD.fulfillment_status='APPLIED' AND NEW.fulfillment_status<>'APPLIED'
 THEN RAISE EXCEPTION 'Applied checkout cannot be downgraded'; END IF;
 IF NEW.fulfillment_status IN ('PENDING','RETRY','APPLIED') AND NEW.status<>'PAID'
 THEN RAISE EXCEPTION 'Fulfillment requires verified payment'; END IF;
 RETURN NEW;
END $$;
