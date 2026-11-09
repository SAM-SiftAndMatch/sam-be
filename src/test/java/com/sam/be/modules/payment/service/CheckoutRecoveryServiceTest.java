package com.sam.be.modules.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.modules.finance.FinanceStore;
import com.sam.be.modules.job.service.JobAddonService;
import com.sam.be.modules.subscription.service.SubscriptionService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CheckoutRecoveryServiceTest {
    CheckoutService checkouts = mock(CheckoutService.class);
    CheckoutRetryService closure = mock(CheckoutRetryService.class);
    FinanceStore store = mock(FinanceStore.class);
    PaymentService payments = mock(PaymentService.class);
    SubscriptionService subscriptions = mock(SubscriptionService.class);
    JobAddonService addons = mock(JobAddonService.class);
    CheckoutRecoveryService service =
            new CheckoutRecoveryService(checkouts, closure, store, payments, subscriptions, addons);
    UUID owner = UUID.randomUUID(), id = UUID.randomUUID(), object = UUID.randomUUID();

    CheckoutService.Checkout order(UUID orderId, String state) {
        return new CheckoutService.Checkout(
                orderId,
                "JOB_ADDON",
                object,
                new BigDecimal("149000"),
                "VND",
                "PAY2S",
                "LIVE",
                state,
                "NONE",
                null,
                null,
                null,
                null,
                null,
                null,
                false);
    }

    @Test
    void activePaidOrUncertainOrdersNeverCreateAnotherCharge() {
        for (String state : List.of("PENDING", "CREATING", "PAID", "UNKNOWN", "REVIEW")) {
            var order = order(id, state);
            when(checkouts.get(owner, id)).thenReturn(order);
            if (Set.of("UNKNOWN", "REVIEW").contains(state))
                assertThrows(ResponseStatusException.class, () -> service.retry(owner, id));
            else assertSame(order, service.retry(owner, id));
        }
        verifyNoInteractions(closure, store, payments, subscriptions, addons);
    }

    @Test
    void replayUsesExistingReplacementInsteadOfCancellingOrCreatingAgain() {
        var replacement = order(UUID.randomUUID(), "PENDING");
        when(checkouts.get(owner, id)).thenReturn(order(id, "CANCELLED"));
        when(checkouts.latest("JOB_ADDON", object)).thenReturn(replacement);
        when(checkouts.get(owner, replacement.orderId())).thenReturn(replacement);
        assertSame(replacement, service.retry(owner, id));
        verifyNoInteractions(closure, store, payments, subscriptions, addons);
    }

    @Test
    void oneClickRetryConfirmsClosureThenReentersOriginalPurchaseWithOwnerAndJob() {
        var next = order(UUID.randomUUID(), "CREATING");
        UUID job = UUID.randomUUID();
        when(checkouts.get(owner, id)).thenReturn(order(id, "EXPIRED"));
        when(store.one(anyString(), eq(object))).thenReturn(Map.of("jobId", job));
        when(addons.checkout(owner, job, null)).thenReturn(next);
        assertSame(next, service.retry(owner, id));
        var sequence = inOrder(closure, addons);
        sequence.verify(closure).prepare(owner, id);
        sequence.verify(addons).checkout(owner, job, null);
    }

    @Test
    void subscriptionRetryKeepsPackageAndTargetProjectAndUsesReplacementCheckout() {
        var sub =
                new CheckoutService.Checkout(
                        id,
                        "SUBSCRIPTION",
                        object,
                        new BigDecimal("149000"),
                        "VND",
                        "PAY2S",
                        "LIVE",
                        "CANCELLED",
                        "NONE",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false);
        var next = order(UUID.randomUUID(), "CREATING");
        UUID pack = UUID.randomUUID(), project = UUID.randomUUID();
        when(checkouts.get(owner, id)).thenReturn(sub);
        when(store.one(anyString(), eq(object)))
                .thenReturn(Map.of("packageId", pack, "targetProjectId", project));
        when(subscriptions.purchase(eq(owner), any()))
                .thenReturn(
                        com.sam.be.modules.subscription.dto.response.UserSubscriptionResponse
                                .builder()
                                .checkout(next)
                                .build());
        when(checkouts.get(owner, next.orderId())).thenReturn(next);
        assertSame(next, service.retry(owner, id));
        verify(subscriptions)
                .purchase(
                        eq(owner),
                        argThat(
                                request ->
                                        pack.equals(request.getPackageId())
                                                && project.equals(request.getProjectId())));
    }

    @Test
    void contractDepositAndMaintenanceRetryUseAuthoritativeDomainApis() {
        UUID contract = UUID.randomUUID(), maintenance = UUID.randomUUID();
        for (String purpose : List.of("CONTRACT_FUND", "SECURITY_DEPOSIT", "MAINTENANCE_FUND")) {
            var original =
                    new CheckoutService.Checkout(
                            id,
                            purpose,
                            object,
                            new BigDecimal("149000"),
                            "VND",
                            "PAY2S",
                            "LIVE",
                            "EXPIRED",
                            "NONE",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            false);
            var next = order(UUID.randomUUID(), "CREATING");
            when(checkouts.get(owner, id)).thenReturn(original);
            when(store.one(anyString(), eq(object)))
                    .thenReturn(Map.of("contractId", contract, "maintenanceOrderId", maintenance));
            var response =
                    com.sam.be.modules.payment.dto.response.PaymentResponse.builder()
                            .orderId(next.orderId().toString().replace("-", ""))
                            .build();
            when(payments.createContractFund(eq(owner), any())).thenReturn(response);
            when(payments.createSecurityDeposit(eq(owner), any())).thenReturn(response);
            when(payments.createMaintenanceFunding(owner, maintenance)).thenReturn(response);
            when(checkouts.get(owner, next.orderId())).thenReturn(next);
            assertSame(next, service.retry(owner, id));
        }
        verify(payments)
                .createContractFund(
                        eq(owner), argThat(request -> contract.equals(request.getContractId())));
        verify(payments)
                .createSecurityDeposit(
                        eq(owner), argThat(request -> contract.equals(request.getContractId())));
        verify(payments).createMaintenanceFunding(owner, maintenance);
    }

    @Test
    void uncertainProviderClosureDoesNotAttemptPurchase() {
        when(checkouts.get(owner, id)).thenReturn(order(id, "EXPIRED"));
        doThrow(new IllegalStateException("not confirmed")).when(closure).prepare(owner, id);
        assertThrows(IllegalStateException.class, () -> service.retry(owner, id));
        verifyNoInteractions(store, payments, subscriptions, addons);
    }

    @Test
    void ownershipFailureStopsAllRecovery() {
        when(checkouts.get(owner, id)).thenThrow(FinanceStore.forbidden());
        assertThrows(ResponseStatusException.class, () -> service.retry(owner, id));
        verifyNoInteractions(closure, store, payments, subscriptions, addons);
    }
}
