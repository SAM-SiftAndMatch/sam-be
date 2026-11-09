package com.sam.be.modules.payment.service;

import com.sam.be.modules.finance.FinanceStore;
import com.sam.be.modules.job.service.JobAddonService;
import com.sam.be.modules.payment.dto.request.CreateEscrowRequest;
import com.sam.be.modules.subscription.dto.request.PurchaseSubscriptionRequest;
import com.sam.be.modules.subscription.service.SubscriptionService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Explicit owner retry: confirm closure, then re-enter the authoritative purchase workflow. */
@Service
@RequiredArgsConstructor
public class CheckoutRecoveryService {
    private final CheckoutService checkouts;
    private final CheckoutRetryService closure;
    private final FinanceStore store;
    private final PaymentService payments;
    private final SubscriptionService subscriptions;
    private final JobAddonService addons;

    @Transactional(propagation = Propagation.NEVER)
    public CheckoutService.Checkout retry(UUID owner, UUID id) {
        var original = checkouts.get(owner, id);
        var latest = checkouts.latest(original.purpose(), original.objectId());
        if (latest != null && !latest.orderId().equals(id)) {
            // Ownership is checked again rather than trusting an object ID from the caller.
            original = checkouts.get(owner, latest.orderId());
            id = original.orderId();
        }
        if (Set.of("PAID", "CREATING", "PENDING").contains(original.paymentStatus()))
            return original;
        if (!Set.of("EXPIRED", "CANCELLED").contains(original.paymentStatus()))
            throw FinanceStore.invalid(
                    "Giao dịch cần xác minh trước khi thanh toán lại. Nếu đã chuyển tiền, không thanh toán thêm.");
        closure.prepare(owner, id);
        // Use today's configured return URL, not an obsolete domain/localhost from an old task.
        String returnUrl = null;
        UUID next;
        switch (original.purpose()) {
            case "SUBSCRIPTION" -> {
                var sub =
                        store.one(
                                "SELECT package_id,target_project_id FROM user_subscriptions WHERE id=?",
                                original.objectId());
                var response =
                        subscriptions.purchase(
                                owner,
                                new PurchaseSubscriptionRequest(
                                        FinanceStore.id(sub, "packageId"),
                                        sub.get("targetProjectId") == null
                                                ? null
                                                : FinanceStore.id(sub, "targetProjectId"),
                                        returnUrl));
                var checkout = response.getCheckout();
                if (checkout == null)
                    throw FinanceStore.invalid(
                            "Gói dịch vụ đã được xử lý. Vui lòng quay lại trang gói dịch vụ.");
                return checkouts.get(owner, checkout.orderId());
            }
            case "JOB_ADDON" -> {
                var order =
                        store.one(
                                "SELECT job_id FROM job_addon_orders WHERE id=?",
                                original.objectId());
                return addons.checkout(owner, FinanceStore.id(order, "jobId"), returnUrl);
            }
            case "CONTRACT_FUND", "SECURITY_DEPOSIT", "MAINTENANCE_FUND" -> {
                var payment =
                        store.one(
                                "SELECT contract_id,maintenance_order_id FROM payments WHERE id=?",
                                original.objectId());
                var request =
                        new CreateEscrowRequest(FinanceStore.id(payment, "contractId"), returnUrl);
                var result =
                        switch (original.purpose()) {
                            case "CONTRACT_FUND" -> payments.createContractFund(owner, request);
                            case "SECURITY_DEPOSIT" ->
                                    payments.createSecurityDeposit(owner, request);
                            default ->
                                    payments.createMaintenanceFunding(
                                            owner, FinanceStore.id(payment, "maintenanceOrderId"));
                        };
                String reference = result.getOrderId();
                if (reference == null || !reference.matches("[a-fA-F0-9]{32}"))
                    throw FinanceStore.invalid(
                            "Chưa xác định được đơn thay thế. Vui lòng kiểm tra lịch sử thanh toán.");
                next =
                        UUID.fromString(
                                reference.substring(0, 8)
                                        + "-"
                                        + reference.substring(8, 12)
                                        + "-"
                                        + reference.substring(12, 16)
                                        + "-"
                                        + reference.substring(16, 20)
                                        + "-"
                                        + reference.substring(20));
            }
            default -> throw FinanceStore.invalid("Nghiệp vụ thanh toán không được hỗ trợ");
        }
        return checkouts.get(owner, next);
    }
}
