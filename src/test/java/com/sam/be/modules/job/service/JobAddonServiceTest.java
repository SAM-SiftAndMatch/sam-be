package com.sam.be.modules.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.constant.enums.SubscriptionStatus;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.modules.finance.Pay2sClient;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.entity.JobAddonOrder;
import com.sam.be.modules.job.repository.JobAddonOrderRepository;
import com.sam.be.modules.job.repository.JobAddonPaymentAttemptRepository;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.notification.service.UserNotificationService;
import com.sam.be.modules.payment.service.CheckoutService;
import com.sam.be.modules.subscription.entity.ServicePackage;
import com.sam.be.modules.subscription.repository.ServicePackageRepository;
import com.sam.be.modules.subscription.repository.UserSubscriptionRepository;
import com.sam.be.modules.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobAddonServiceTest {
    private static final UUID FEATURED_ID = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID URGENT_ID = UUID.fromString("f0000000-0000-0000-0000-000000000002");

    private final JobRepository jobs = mock(JobRepository.class);
    private final JobAddonOrderRepository orders = mock(JobAddonOrderRepository.class);
    private final JobAddonPaymentAttemptRepository attempts =
            mock(JobAddonPaymentAttemptRepository.class);
    private final ServicePackageRepository packages = mock(ServicePackageRepository.class);
    private final UserSubscriptionRepository subscriptions = mock(UserSubscriptionRepository.class);
    private final JobService jobService = mock(JobService.class);
    private final CheckoutService checkouts = mock(CheckoutService.class);
    private final Pay2sClient pay2s = mock(Pay2sClient.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final UserNotificationService userNotifications = mock(UserNotificationService.class);
    private final CacheInvalidationService cache = mock(CacheInvalidationService.class);

    private JobAddonService service;

    @BeforeEach
    void setUp() {
        service =
                new JobAddonService(
                        jobs,
                        orders,
                        checkouts,
                        pay2s,
                        packages,
                        subscriptions,
                        jobService,
                        notifications,
                        userNotifications,
                        cache);
    }

    @Test
    void selectingPaidAddonsCreatesPendingOrderWithoutActivatingJob() {
        UUID clientId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Job job =
                Job.builder()
                        .id(jobId)
                        .client(User.builder().id(clientId).build())
                        .title("Test project")
                        .status(JobStatus.OPEN)
                        .deadline(LocalDateTime.now().plusDays(7))
                        .isFeatured(false)
                        .isUrgentHiring(false)
                        .build();
        when(jobs.findByIdForWorkflowUpdate(jobId)).thenReturn(Optional.of(job));
        when(jobs.findById(jobId)).thenReturn(Optional.of(job));
        when(orders.findFirstByJobIdAndStatusOrderByCreatedAtDesc(
                        jobId, JobAddonOrder.Status.PENDING))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(savedOrder));
        when(orders.save(any(JobAddonOrder.class)))
                .thenAnswer(
                        invocation -> {
                            savedOrder = invocation.getArgument(0);
                            savedOrder.setId(UUID.randomUUID());
                            return savedOrder;
                        });
        when(packages.findById(FEATURED_ID))
                .thenReturn(
                        Optional.of(
                                ServicePackage.builder()
                                        .isActive(true)
                                        .price(BigDecimal.valueOf(59000))
                                        .build()));
        when(packages.findById(URGENT_ID))
                .thenReturn(
                        Optional.of(
                                ServicePackage.builder()
                                        .isActive(true)
                                        .price(BigDecimal.valueOf(99000))
                                        .build()));

        var summary = service.select(clientId, jobId, true, true);

        assertEquals("PENDING", summary.orderStatus());
        assertEquals(0, BigDecimal.valueOf(158000).compareTo(summary.totalDue()));
        assertTrue(summary.featuredPending());
        assertTrue(summary.urgentPending());
        assertFalse(job.getIsFeatured());
        assertFalse(job.getIsUrgentHiring());
        verify(jobService, never()).activateAddons(eq(jobId), anyBoolean(), anyBoolean());
        verify(notifications)
                .sendJobAddonNotification(
                        eq(clientId),
                        eq(jobId),
                        eq("Test project"),
                        eq(BigDecimal.valueOf(158000)),
                        eq("PROJECT_ADDON_PAYMENT_PENDING"),
                        any(String.class));
    }

    private JobAddonOrder savedOrder;

    @Test
    void paidAddonActivatesOnceAndStoresEnvironment() {
        UUID jobId = UUID.randomUUID(), clientId = UUID.randomUUID(), orderId = UUID.randomUUID();
        Job job =
                Job.builder()
                        .id(jobId)
                        .client(User.builder().id(clientId).build())
                        .title("Project")
                        .status(JobStatus.OPEN)
                        .deadline(LocalDateTime.now().plusDays(3))
                        .build();
        JobAddonOrder order =
                JobAddonOrder.builder()
                        .id(orderId)
                        .job(job)
                        .client(job.getClient())
                        .featured(true)
                        .featuredFee(BigDecimal.valueOf(59000))
                        .totalFee(BigDecimal.valueOf(59000))
                        .status(JobAddonOrder.Status.PENDING)
                        .build();
        when(orders.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(pay2s.environment()).thenReturn("SANDBOX");
        when(packages.findById(FEATURED_ID))
                .thenReturn(
                        Optional.of(
                                ServicePackage.builder()
                                        .id(FEATURED_ID)
                                        .isActive(true)
                                        .price(BigDecimal.valueOf(59000))
                                        .build()));
        assertEquals("APPLIED", service.activateVerifiedPayment(orderId, "test", "SANDBOX"));
        assertEquals("APPLIED", service.activateVerifiedPayment(orderId, "test", "SANDBOX"));
        verify(jobService, times(1)).activateAddons(jobId, true, false);
        var saved =
                org.mockito.ArgumentCaptor.forClass(
                        com.sam.be.modules.subscription.entity.UserSubscription.class);
        verify(subscriptions).save(saved.capture());
        assertEquals("SANDBOX", saved.getValue().getEnvironment());
        assertEquals(SubscriptionStatus.ACTIVE, saved.getValue().getStatus());
    }

    @Test
    void moneyForClosedProjectIsRecordedForReviewWithoutActivatingFeatures() {
        UUID id = UUID.randomUUID();
        var user = User.builder().id(UUID.randomUUID()).build();
        var job =
                Job.builder()
                        .id(UUID.randomUUID())
                        .client(user)
                        .status(JobStatus.CANCELLED)
                        .build();
        var order =
                JobAddonOrder.builder()
                        .id(id)
                        .job(job)
                        .client(user)
                        .status(JobAddonOrder.Status.PENDING)
                        .totalFee(BigDecimal.valueOf(59000))
                        .build();
        when(orders.findByIdForUpdate(id)).thenReturn(Optional.of(order));
        assertEquals("REVIEW", service.activateVerifiedPayment(id, "receipt", "LIVE"));
        assertEquals(JobAddonOrder.Status.PAID_UNAPPLIED, order.getStatus());
        verify(jobService, never()).activateAddons(any(), anyBoolean(), anyBoolean());
    }
}
