package com.sam.be.modules.job.service;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.constant.enums.SubscriptionStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpayClient;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpayProperties;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpaySigner;
import com.sam.be.modules.job.dto.response.JobAddonCheckoutResponse;
import com.sam.be.modules.job.dto.response.JobAddonPriceResponse;
import com.sam.be.modules.job.dto.response.JobAddonSummaryResponse;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.entity.JobAddonOrder;
import com.sam.be.modules.job.entity.JobAddonPaymentAttempt;
import com.sam.be.modules.job.repository.JobAddonOrderRepository;
import com.sam.be.modules.job.repository.JobAddonPaymentAttemptRepository;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.notification.service.UserNotificationService;
import com.sam.be.modules.subscription.entity.ServicePackage;
import com.sam.be.modules.subscription.entity.UserSubscription;
import com.sam.be.modules.subscription.repository.ServicePackageRepository;
import com.sam.be.modules.subscription.repository.UserSubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobAddonService {
    private static final UUID FEATURED_ID = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID URGENT_ID = UUID.fromString("f0000000-0000-0000-0000-000000000002");
    private static final UUID BUSINESS_ID = UUID.fromString("f0000000-0000-0000-0000-000000000005");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final JobRepository jobRepository;
    private final JobAddonOrderRepository orderRepository;
    private final JobAddonPaymentAttemptRepository attemptRepository;
    private final ServicePackageRepository packageRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final JobService jobService;
    private final VnpayClient vnpayClient;
    private final VnpaySigner vnpaySigner;
    private final VnpayProperties vnpayProperties;
    private final NotificationService notificationService;
    private final UserNotificationService userNotificationService;
    private final CacheInvalidationService cacheInvalidationService;

    @Transactional(readOnly = true)
    public JobAddonPriceResponse prices(UUID clientId) {
        boolean included = hasBusiness(clientId);
        return new JobAddonPriceResponse(
                included ? ZERO : availablePackage(FEATURED_ID).getPrice(),
                included ? ZERO : availablePackage(URGENT_ID).getPrice(), included);
    }

    @Transactional
    public JobAddonSummaryResponse select(UUID clientId, UUID jobId, boolean featured, boolean urgent) {
        Job job = ownedOpenJob(clientId, jobId);
        boolean needsFeatured = featured && !Boolean.TRUE.equals(job.getIsFeatured());
        boolean needsUrgent = urgent && !Boolean.TRUE.equals(job.getIsUrgentHiring());
        if (!needsFeatured && !needsUrgent) return summary(clientId, jobId);
        var pending = orderRepository.findFirstByJobIdAndStatusOrderByCreatedAtDesc(
                jobId, JobAddonOrder.Status.PENDING);
        if (hasBusiness(clientId)) {
            if (pending.isPresent()) {
                pending.get().setStatus(JobAddonOrder.Status.CANCELLED);
                userNotificationService.resolveJobAddonReminder(clientId, jobId);
            }
            jobService.activateAddons(jobId, needsFeatured, needsUrgent);
            return summary(clientId, jobId);
        }
        if (pending.isPresent()) {
            JobAddonOrder order = pending.get();
            if (order.isFeatured() == needsFeatured && order.isUrgent() == needsUrgent)
                return summary(clientId, jobId);
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE,
                    "Dự án đã có lựa chọn tính năng chờ thanh toán; hãy thanh toán trước khi chọn thêm");
        }
        BigDecimal featuredFee = needsFeatured ? availablePackage(FEATURED_ID).getPrice() : ZERO;
        BigDecimal urgentFee = needsUrgent ? availablePackage(URGENT_ID).getPrice() : ZERO;
        JobAddonOrder order = orderRepository.save(JobAddonOrder.builder()
                .job(job).client(job.getClient()).featured(needsFeatured).urgent(needsUrgent)
                .featuredFee(featuredFee).urgentFee(urgentFee)
                .totalFee(featuredFee.add(urgentFee)).status(JobAddonOrder.Status.PENDING)
                .createdAt(LocalDateTime.now()).build());
        notificationService.sendJobAddonNotification(clientId, jobId, job.getTitle(), order.getTotalFee(),
                "PROJECT_ADDON_PAYMENT_PENDING",
                "Dự án “" + job.getTitle() + "” chưa thanh toán tính năng; các tính năng đã chọn chưa được kích hoạt.");
        return summary(clientId, jobId);
    }

    @Transactional(readOnly = true)
    public JobAddonSummaryResponse summary(UUID clientId, UUID jobId) {
        Job job = ownedJob(clientId, jobId);
        JobAddonOrder order = orderRepository.findFirstByJobIdAndStatusOrderByCreatedAtDesc(
                        jobId, JobAddonOrder.Status.PENDING)
                .or(() -> orderRepository.findFirstByJobIdOrderByCreatedAtDesc(jobId))
                .orElse(null);
        boolean pending = order != null && order.getStatus() == JobAddonOrder.Status.PENDING;
        return new JobAddonSummaryResponse(
                order == null ? null : order.getId(),
                order == null ? null : order.getStatus().name(),
                Boolean.TRUE.equals(job.getIsFeatured()), Boolean.TRUE.equals(job.getIsUrgentHiring()),
                pending && order.isFeatured(), pending && order.isUrgent(),
                pending ? order.getFeaturedFee() : ZERO,
                pending ? order.getUrgentFee() : ZERO,
                pending ? order.getTotalFee() : ZERO,
                hasBusiness(clientId));
    }

    @Transactional
    public JobAddonCheckoutResponse checkout(UUID clientId, UUID jobId, String returnUrl) {
        ownedOpenJob(clientId, jobId);
        JobAddonOrder order = orderRepository.findFirstByJobIdAndStatusOrderByCreatedAtDesc(
                        jobId, JobAddonOrder.Status.PENDING)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Không có tính năng nào đang chờ thanh toán"));
        order = orderRepository.findByIdForUpdate(order.getId()).orElseThrow();
        if (returnUrl != null && !vnpayProperties.isReturnUrlAllowed(returnUrl))
            throw new ApiException(ErrorCode.INVALID_RETURN_URL);
        LocalDateTime now = LocalDateTime.now();
        var current = attemptRepository.findFirstByOrderIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                order.getId(), JobAddonPaymentAttempt.Status.PENDING, now);
        if (current.isPresent()) return new JobAddonCheckoutResponse(order.getId(), current.get().getPaymentUrl());
        String txnRef = "JA" + UUID.randomUUID().toString().replace("-", "");
        long amount = order.getTotalFee().multiply(BigDecimal.valueOf(100)).longValueExact();
        String url = vnpayClient.buildPaymentUrl(txnRef, amount,
                "Thanh toan tinh nang du an " + jobId, null, returnUrl);
        attemptRepository.save(JobAddonPaymentAttempt.builder().order(order).txnRef(txnRef)
                .status(JobAddonPaymentAttempt.Status.PENDING).paymentUrl(url)
                .createdAt(now).expiresAt(now.plusMinutes(15)).build());
        return new JobAddonCheckoutResponse(order.getId(), url);
    }

    @Transactional
    public Map<String, String> handleVnpayIpn(Map<String, String> params) {
        if (!vnpaySigner.verify(params, vnpayProperties.getHashSecret())
                || !vnpayProperties.getTmnCode().equals(params.get("vnp_TmnCode")))
            return Map.of("RspCode", "97", "Message", "Invalid signature or merchant");
        JobAddonPaymentAttempt attempt = attemptRepository.findByTxnRefForUpdate(
                params.get("vnp_TxnRef")).orElse(null);
        if (attempt == null) return Map.of("RspCode", "01", "Message", "Order not found");
        JobAddonOrder order = orderRepository.findByIdForUpdate(attempt.getOrder().getId()).orElseThrow();
        long received;
        try { received = Long.parseLong(params.get("vnp_Amount")); }
        catch (NumberFormatException | NullPointerException e) {
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }
        long expected = order.getTotalFee().multiply(BigDecimal.valueOf(100)).longValueExact();
        if (received != expected) return Map.of("RspCode", "04", "Message", "Invalid amount");
        boolean paid = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));
        if (!paid) {
            if (attempt.getStatus() == JobAddonPaymentAttempt.Status.PENDING)
                attempt.setStatus(JobAddonPaymentAttempt.Status.FAILED);
            return Map.of("RspCode", "00", "Message", "Payment not completed");
        }
        if (attempt.getStatus() == JobAddonPaymentAttempt.Status.PAID
                || attempt.getStatus() == JobAddonPaymentAttempt.Status.PAID_DUPLICATE)
            return Map.of("RspCode", "02", "Message", "Already confirmed");
        attempt.setPaidAt(LocalDateTime.now());
        if (order.getStatus() != JobAddonOrder.Status.PENDING) {
            attempt.setStatus(JobAddonPaymentAttempt.Status.PAID_DUPLICATE);
            notificationService.sendJobAddonNotification(order.getClient().getId(), order.getJob().getId(),
                    order.getJob().getTitle(), order.getTotalFee(), "PROJECT_ADDON_PAYMENT_REVIEW",
                    "Đã ghi nhận thêm một giao dịch cho tính năng dự án. Vui lòng liên hệ hỗ trợ để đối soát khoản tiền.");
            return Map.of("RspCode", "02", "Message", "Duplicate payment requires review");
        }
        attempt.setStatus(JobAddonPaymentAttempt.Status.PAID);
        Job job = order.getJob();
        order.setPaidAt(LocalDateTime.now());
        if (job.getStatus() != JobStatus.OPEN ||
                (job.getDeadline() != null && !job.getDeadline().isAfter(LocalDateTime.now()))) {
            order.setStatus(JobAddonOrder.Status.PAID_UNAPPLIED);
            notificationService.sendJobAddonNotification(order.getClient().getId(), job.getId(),
                    job.getTitle(), order.getTotalFee(), "PROJECT_ADDON_PAYMENT_REVIEW",
                    "Đã ghi nhận thanh toán nhưng dự án không còn tuyển. Vui lòng liên hệ hỗ trợ để xử lý khoản tiền.");
            return Map.of("RspCode", "00", "Message", "Paid; manual review required");
        }
        jobService.activateAddons(job.getId(), order.isFeatured(), order.isUrgent());
        order.setStatus(JobAddonOrder.Status.PAID);
        createPurchaseHistory(order, attempt.getTxnRef());
        userNotificationService.resolveJobAddonReminder(order.getClient().getId(), job.getId());
        notificationService.sendJobAddonNotification(order.getClient().getId(), job.getId(),
                job.getTitle(), order.getTotalFee(), "PROJECT_ADDON_PAYMENT_PAID",
                "Đã thanh toán và kích hoạt tính năng cho dự án “" + job.getTitle() + "”.");
        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }

    private void createPurchaseHistory(JobAddonOrder order, String txnRef) {
        if (order.isFeatured()) saveSubscription(order, FEATURED_ID, txnRef);
        if (order.isUrgent()) saveSubscription(order, URGENT_ID, txnRef);
        cacheInvalidationService.subscriptionChanged(order.getClient().getId());
    }

    private void saveSubscription(JobAddonOrder order, UUID packageId, String txnRef) {
        subscriptionRepository.save(UserSubscription.builder().user(order.getClient())
                .servicePackage(availablePackage(packageId)).targetProject(order.getJob())
                .startDate(LocalDateTime.now()).endDate(order.getJob().getDeadline())
                .status(SubscriptionStatus.ACTIVE).gatewayTxnRef(txnRef + packageId).build());
    }

    private ServicePackage availablePackage(UUID id) {
        ServicePackage servicePackage = packageRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_AVAILABLE));
        if (!Boolean.TRUE.equals(servicePackage.getIsActive()) || servicePackage.getPrice() == null)
            throw new ApiException(ErrorCode.PACKAGE_NOT_AVAILABLE);
        return servicePackage;
    }

    private boolean hasBusiness(UUID userId) {
        return subscriptionRepository.hasActivePackage(userId, BUSINESS_ID,
                SubscriptionStatus.ACTIVE, LocalDateTime.now());
    }

    private Job ownedJob(UUID clientId, UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!job.getClient().getId().equals(clientId)) throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        return job;
    }

    private Job ownedOpenJob(UUID clientId, UUID jobId) {
        Job job = jobRepository.findByIdForWorkflowUpdate(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!job.getClient().getId().equals(clientId)) throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        if (job.getStatus() != JobStatus.OPEN ||
                (job.getDeadline() != null && !job.getDeadline().isAfter(LocalDateTime.now())))
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Dự án đã ngừng nhận ứng tuyển");
        return job;
    }
}
