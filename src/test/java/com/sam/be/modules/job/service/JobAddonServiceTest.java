package com.sam.be.modules.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpayClient;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpayProperties;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpaySigner;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.entity.JobAddonOrder;
import com.sam.be.modules.job.entity.JobAddonPaymentAttempt;
import com.sam.be.modules.job.repository.JobAddonOrderRepository;
import com.sam.be.modules.job.repository.JobAddonPaymentAttemptRepository;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.notification.service.UserNotificationService;
import com.sam.be.modules.subscription.entity.ServicePackage;
import com.sam.be.modules.subscription.repository.ServicePackageRepository;
import com.sam.be.modules.subscription.repository.UserSubscriptionRepository;
import com.sam.be.modules.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobAddonServiceTest {
    private static final UUID FEATURED_ID = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID URGENT_ID = UUID.fromString("f0000000-0000-0000-0000-000000000002");

    private final JobRepository jobs = mock(JobRepository.class);
    private final JobAddonOrderRepository orders = mock(JobAddonOrderRepository.class);
    private final JobAddonPaymentAttemptRepository attempts = mock(JobAddonPaymentAttemptRepository.class);
    private final ServicePackageRepository packages = mock(ServicePackageRepository.class);
    private final UserSubscriptionRepository subscriptions = mock(UserSubscriptionRepository.class);
    private final JobService jobService = mock(JobService.class);
    private final VnpayClient vnpayClient = mock(VnpayClient.class);
    private final VnpaySigner signer = mock(VnpaySigner.class);
    private final VnpayProperties properties = mock(VnpayProperties.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final UserNotificationService userNotifications = mock(UserNotificationService.class);
    private final CacheInvalidationService cache = mock(CacheInvalidationService.class);

    private JobAddonService service;

    @BeforeEach
    void setUp() {
        service = new JobAddonService(jobs, orders, attempts, packages, subscriptions,
                jobService, vnpayClient, signer, properties, notifications, userNotifications, cache);
    }

    @Test
    void selectingPaidAddonsCreatesPendingOrderWithoutActivatingJob() {
        UUID clientId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Job job = Job.builder().id(jobId).client(User.builder().id(clientId).build())
                .title("Test project").status(JobStatus.OPEN)
                .deadline(LocalDateTime.now().plusDays(7))
                .isFeatured(false).isUrgentHiring(false).build();
        when(jobs.findByIdForWorkflowUpdate(jobId)).thenReturn(Optional.of(job));
        when(jobs.findById(jobId)).thenReturn(Optional.of(job));
        when(orders.findFirstByJobIdAndStatusOrderByCreatedAtDesc(jobId, JobAddonOrder.Status.PENDING))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.of(savedOrder));
        when(orders.save(any(JobAddonOrder.class))).thenAnswer(invocation -> {
            savedOrder = invocation.getArgument(0);
            savedOrder.setId(UUID.randomUUID());
            return savedOrder;
        });
        when(packages.findById(FEATURED_ID)).thenReturn(Optional.of(
                ServicePackage.builder().isActive(true).price(BigDecimal.valueOf(59000)).build()));
        when(packages.findById(URGENT_ID)).thenReturn(Optional.of(
                ServicePackage.builder().isActive(true).price(BigDecimal.valueOf(99000)).build()));

        var summary = service.select(clientId, jobId, true, true);

        assertEquals("PENDING", summary.orderStatus());
        assertEquals(0, BigDecimal.valueOf(158000).compareTo(summary.totalDue()));
        assertTrue(summary.featuredPending());
        assertTrue(summary.urgentPending());
        assertFalse(job.getIsFeatured());
        assertFalse(job.getIsUrgentHiring());
        verify(jobService, never()).activateAddons(eq(jobId), anyBoolean(), anyBoolean());
        verify(notifications).sendJobAddonNotification(eq(clientId), eq(jobId), eq("Test project"),
                eq(BigDecimal.valueOf(158000)), eq("PROJECT_ADDON_PAYMENT_PENDING"), any(String.class));
    }

    private JobAddonOrder savedOrder;

    @Test
    void verifiedIpnActivatesOnceAndDuplicateDoesNotChargeOrActivateTwice() {
        UUID clientId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        User client = User.builder().id(clientId).build();
        Job job = Job.builder().id(jobId).client(client).title("Test project")
                .status(JobStatus.OPEN).deadline(LocalDateTime.now().plusDays(7)).build();
        JobAddonOrder order = JobAddonOrder.builder().id(UUID.randomUUID()).job(job).client(client)
                .featured(true).featuredFee(BigDecimal.valueOf(59000))
                .urgentFee(BigDecimal.ZERO).totalFee(BigDecimal.valueOf(59000))
                .status(JobAddonOrder.Status.PENDING).build();
        JobAddonPaymentAttempt attempt = JobAddonPaymentAttempt.builder().id(UUID.randomUUID())
                .order(order).txnRef("JA123").status(JobAddonPaymentAttempt.Status.PENDING).build();
        when(properties.getHashSecret()).thenReturn("secret");
        when(properties.getTmnCode()).thenReturn("merchant");
        when(signer.verify(any(), eq("secret"))).thenReturn(true);
        when(attempts.findByTxnRefForUpdate("JA123")).thenReturn(Optional.of(attempt));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(packages.findById(FEATURED_ID)).thenReturn(Optional.of(
                ServicePackage.builder().id(FEATURED_ID).isActive(true)
                        .price(BigDecimal.valueOf(59000)).build()));
        Map<String, String> params = Map.of("vnp_TxnRef", "JA123", "vnp_Amount", "5900000",
                "vnp_TmnCode", "merchant", "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00");

        assertEquals("00", service.handleVnpayIpn(params).get("RspCode"));
        assertEquals(JobAddonOrder.Status.PAID, order.getStatus());
        assertEquals(JobAddonPaymentAttempt.Status.PAID, attempt.getStatus());
        assertEquals("02", service.handleVnpayIpn(params).get("RspCode"));
        verify(jobService).activateAddons(jobId, true, false);
        verify(subscriptions).save(any());
        verify(userNotifications).resolveJobAddonReminder(clientId, jobId);
    }

    @Test
    void forgedIpnCannotActivate() {
        when(properties.getHashSecret()).thenReturn("secret");
        when(signer.verify(any(), eq("secret"))).thenReturn(false);

        assertEquals("97", service.handleVnpayIpn(Map.of("vnp_TxnRef", "JA123")).get("RspCode"));
        verify(jobService, never()).activateAddons(any(), anyBoolean(), anyBoolean());
    }
}
