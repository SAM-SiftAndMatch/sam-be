package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.common.constant.enums.*;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.payment.service.CheckoutService;
import com.sam.be.modules.subscription.entity.*;
import com.sam.be.modules.subscription.repository.*;
import com.sam.be.modules.subscription.service.impl.SubscriptionServiceImpl;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

class SubscriptionVerifiedPaymentTest {
    @Mock UserSubscriptionRepository subscriptions;
    @Mock ServicePackageRepository packages;
    @Mock UserRepository users;
    @Mock FinanceStore store;
    @Mock Pay2sClient provider;
    @Mock CheckoutService checkouts;
    @Mock NotificationService notifications;
    @Mock CacheInvalidationService cache;
    @InjectMocks SubscriptionServiceImpl service;
    AutoCloseable mocks;
    UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
    ServicePackage plan;
    UserSubscription sub;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        plan =
                ServicePackage.builder()
                        .id(UUID.fromString("f0000000-0000-0000-0000-000000000006"))
                        .name("PRO DEV")
                        .type(PackageType.SUBSCRIPTION)
                        .isActive(true)
                        .price(new BigDecimal("999999"))
                        .build();
        sub =
                UserSubscription.builder()
                        .id(id)
                        .user(User.builder().id(owner).role(UserRole.FREELANCER).build())
                        .servicePackage(plan)
                        .environment("LIVE")
                        .amountPaid(new BigDecimal("149000"))
                        .status(SubscriptionStatus.PENDING)
                        .build();
        when(subscriptions.findByIdForUpdate(id)).thenReturn(Optional.of(sub));
        when(provider.environment()).thenReturn("LIVE");
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void paidPriceSnapshotActivatesOnceAndDuplicateDoesNotExtendThirtyDays() {
        assertEquals(
                "APPLIED", service.activateVerifiedPayment(id, "LIVE", new BigDecimal("149000")));
        var end = sub.getEndDate();
        assertEquals(sub.getStartDate().plusDays(30), end);
        assertEquals(
                "APPLIED", service.activateVerifiedPayment(id, "LIVE", new BigDecimal("149000")));
        assertEquals(end, sub.getEndDate());
        verify(subscriptions, times(1)).save(sub);
        verify(notifications, times(1))
                .sendSubscriptionNotification(
                        eq(owner), eq(id), eq("SUBSCRIPTION_ACTIVE"), anyString());
    }

    @Test
    void sandboxMoneyCannotActivateLiveBenefits() {
        assertEquals(
                "REVIEW", service.activateVerifiedPayment(id, "SANDBOX", new BigDecimal("149000")));
        assertEquals(SubscriptionStatus.PENDING, sub.getStatus());
        verify(subscriptions, never()).save(any());
    }

    @Test
    void mismatchedPaymentCannotActivatePackage() {
        assertEquals("REVIEW", service.activateVerifiedPayment(id, "LIVE", BigDecimal.ONE));
        verify(subscriptions, never()).save(any());
    }

    @Test
    void pendingPurchaseReusesSnapshotInsteadOfChargingTheNewCatalogPrice() {
        when(packages.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(users.findById(owner)).thenReturn(Optional.of(sub.getUser()));
        when(subscriptions.findByUser_Id(owner)).thenReturn(List.of(sub));
        var checkout =
                new CheckoutService.Checkout(
                        UUID.randomUUID(),
                        "SUBSCRIPTION",
                        id,
                        new BigDecimal("149000"),
                        "VND",
                        "PAY2S",
                        "LIVE",
                        "PENDING",
                        "NONE",
                        "https://payment.pay2s.vn/pay/test",
                        "SAMGOITEST01",
                        "2026-11-07T00:00:00Z",
                        "2026-11-06T00:00:00Z",
                        null,
                        "SAMTEST",
                        false);
        when(checkouts.create(
                        eq("SUBSCRIPTION"),
                        eq(id),
                        eq(owner),
                        eq(new BigDecimal("149000")),
                        isNull()))
                .thenReturn(checkout);
        service.purchase(
                owner,
                new com.sam.be.modules.subscription.dto.request.PurchaseSubscriptionRequest(
                        plan.getId(), null, null));
        verify(checkouts).create("SUBSCRIPTION", id, owner, new BigDecimal("149000"), null);
    }
}
