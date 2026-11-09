package com.sam.be.modules.payment.service;

import com.sam.be.modules.finance.FinanceStore;
import com.sam.be.modules.finance.Pay2sClient;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Close an unpaid expired provider order before the buyer retries through its business API. */
@Service
@RequiredArgsConstructor
public class CheckoutRetryService {
    private final FinanceStore store;
    private final Pay2sClient provider;
    private final CheckoutService checkouts;
    private final PaymentReconciliationService cases;
    private final PlatformTransactionManager transactions;

    @Transactional(propagation = Propagation.NEVER)
    public CheckoutService.Checkout prepare(UUID owner, UUID id) {
        var tx = new TransactionTemplate(transactions);
        boolean alreadyClosed = Boolean.TRUE.equals(tx.execute(status -> inspect(owner, id)));
        if (alreadyClosed) return checkouts.get(owner, id);

        // No database locks/transaction during network I/O. A lost acknowledgement is safe
        // to retry: provider cancellation is idempotent and the checkout ID never changes.
        var confirmation = provider.cancel(id.toString().replace("-", ""));
        if (confirmation == null || confirmation.requestId() == null)
            throw FinanceStore.invalid("Chưa xác nhận đóng đơn cũ, chưa thể thanh toán lại");
        tx.executeWithoutResult(
                status -> {
                    if (inspect(owner, id)) return;
                    store.jdbc()
                            .update(
                                    "UPDATE payment_creation_tasks SET"
                                            + " state='REJECTED',generation=generation+1,locked_until=NULL,updated_at=clock_timestamp()"
                                            + " WHERE checkout_id=?",
                                    id);
                    store.jdbc()
                            .update(
                                    "UPDATE payment_checkouts SET"
                                            + " status='CANCELLED',replacement_allowed=true WHERE id=?",
                                    id);
                    String key = "checkout:" + id + ":CHECKOUT_EXPIRED";
                    cases.open(key, id, null, "CHECKOUT_EXPIRED");
                    UUID caseId =
                            FinanceStore.id(
                                    store.one(
                                            "SELECT id FROM payment_reconciliation_cases WHERE"
                                                    + " case_key=? FOR UPDATE",
                                            key),
                                    "id");
                    store.jdbc()
                            .update(
                                    "INSERT INTO"
                                            + " payment_reconciliation_events(id,case_id,actor_id,action,evidence_reference)"
                                            + " VALUES (?,?,?,'PROVIDER_CLOSED_FOR_RETRY',?)",
                                    UUID.randomUUID(),
                                    caseId,
                                    owner,
                                    "Pay2S cancel request: " + confirmation.requestId());
                    store.jdbc()
                            .update(
                                    "UPDATE payment_reconciliation_cases SET"
                                            + " state='RESOLVED',resolution='PROVIDER_CLOSED_NO_PAYMENT',resolved_at=clock_timestamp(),updated_at=clock_timestamp()"
                                            + " WHERE id=?",
                                    caseId);
                });
        return checkouts.get(owner, id);
    }

    private boolean inspect(UUID owner, UUID id) {
        Map<String, Object> row =
                store.one(
                        "SELECT *,expires_at<=clock_timestamp() AS expired_now FROM"
                                + " payment_checkouts WHERE id=? FOR UPDATE",
                        id);
        if (!owner.equals(FinanceStore.id(row, "ownerId"))) throw FinanceStore.forbidden();
        boolean closed =
                "CANCELLED".equals(row.get("status"))
                        && Boolean.TRUE.equals(row.get("replacementAllowed"));
        if (!provider.environment().equals(row.get("environment"))
                || !closed && !provider.merchantScope().equals(row.get("merchantScope")))
            throw FinanceStore.invalid(
                    "Đơn thuộc cấu hình thanh toán cũ; cần hỗ trợ đối soát trước khi tạo lại");
        if (!closed
                && (!Set.of("PENDING", "EXPIRED", "CANCELLED").contains(row.get("status"))
                        || !"CANCELLED".equals(row.get("status"))
                                && !Boolean.TRUE.equals(row.get("expiredNow"))))
            throw FinanceStore.invalid("Chỉ khôi phục được liên kết đã hết hạn và chưa có tiền");
        if (!store.rows("SELECT id FROM payment_checkout_receipts WHERE checkout_id=?", id)
                        .isEmpty()
                || !store.rows(
                                "SELECT id FROM payment_provider_events WHERE order_reference=? AND"
                                        + " result_code=0",
                                id.toString().replace("-", ""))
                        .isEmpty())
            throw FinanceStore.invalid(
                    "Đã có thông tin nhận tiền; không thanh toán lại, hãy liên hệ hỗ trợ");
        if (!store.rows(
                        "SELECT checkout_id FROM payment_creation_tasks WHERE checkout_id=? AND"
                                + " state='LEASED' AND locked_until>clock_timestamp()",
                        id)
                .isEmpty())
            throw FinanceStore.invalid("Đơn đang được xử lý, vui lòng kiểm tra lại sau");
        return closed;
    }
}
