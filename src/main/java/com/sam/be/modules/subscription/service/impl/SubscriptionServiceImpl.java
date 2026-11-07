package com.sam.be.modules.subscription.service.impl;

import com.sam.be.common.constant.enums.PackageType;
import com.sam.be.common.constant.enums.SubscriptionStatus;
import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.PageRequests;
import com.sam.be.common.response.PageResponse;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.CacheInvalidationService;
import com.sam.be.infrastructure.cache.service.RedisCacheService;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.notification.service.NotificationService;
import com.sam.be.modules.subscription.dto.request.PurchaseSubscriptionRequest;
import com.sam.be.modules.subscription.dto.response.UserSubscriptionResponse;
import com.sam.be.modules.subscription.entity.ServicePackage;
import com.sam.be.modules.subscription.entity.UserSubscription;
import com.sam.be.modules.subscription.repository.ServicePackageRepository;
import com.sam.be.modules.subscription.repository.UserSubscriptionRepository;
import com.sam.be.modules.subscription.service.SubscriptionService;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final ServicePackageRepository servicePackageRepository;

    private final UserSubscriptionRepository userSubscriptionRepository;

    private final UserRepository userRepository;
    private final com.sam.be.modules.payment.service.CheckoutService checkouts;
    private final com.sam.be.modules.finance.FinanceStore store;
    private final com.sam.be.modules.finance.Pay2sClient pay2s;

    private final JobRepository jobRepository;

    private final NotificationService notificationService;

    private final RedisCacheService redisCacheService;
    private final CacheInvalidationService cacheInvalidationService;

    @Override
    @Transactional(noRollbackFor = com.sam.be.common.exception.PaymentGatewayException.class)
    public UserSubscriptionResponse purchase(UUID userId, PurchaseSubscriptionRequest request) {
        if (request.getPackageId() == null) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        var replay =
                checkouts.replay(
                        userId,
                        "SUBSCRIPTION",
                        request.getPackageId() + ":" + request.getProjectId());
        if (replay != null)
            return toResponse(
                    userSubscriptionRepository.findById(replay.objectId()).orElseThrow(), replay);
        store.lockOwner(userId);
        ServicePackage plan =
                servicePackageRepository
                        .findById(request.getPackageId())
                        .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_AVAILABLE));
        User user = userRepository.findById(userId).orElseThrow();
        String suffix = plan.getId().toString();
        boolean business = suffix.endsWith("000000000005"), pro = suffix.endsWith("000000000006");
        if (!Boolean.TRUE.equals(plan.getIsActive())
                || Boolean.TRUE.equals(plan.getIsDynamicPrice())
                || plan.getPrice() == null
                || plan.getPrice().signum() <= 0)
            throw new ApiException(ErrorCode.PACKAGE_NOT_AVAILABLE);
        if ((business && user.getRole() != UserRole.CLIENT)
                || (pro && user.getRole() != UserRole.FREELANCER))
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        if (suffix.endsWith("000000000001") || suffix.endsWith("000000000002"))
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED,
                    "Mua tính năng này trong dự án để áp dụng đúng dự án.");
        Job project = null;
        if (plan.getType() == PackageType.PAY_PER_USE) {
            if (user.getRole() != UserRole.CLIENT || request.getProjectId() == null)
                throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
            project = jobRepository.findByIdForWorkflowUpdate(request.getProjectId()).orElseThrow();
            if (!project.getClient().getId().equals(userId))
                throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
            // Paid project upgrades use the addon API; unsupported products must not take money.
            throw new ApiException(ErrorCode.PACKAGE_NOT_AVAILABLE);
        }
        if (!business && !pro) throw new ApiException(ErrorCode.PACKAGE_NOT_AVAILABLE);
        var candidates =
                userSubscriptionRepository.findByUser_Id(userId).stream()
                        .filter(
                                x ->
                                        x.getServicePackage().getId().equals(plan.getId())
                                                && pay2s.environment().equals(x.getEnvironment()))
                        .toList();
        var active =
                candidates.stream()
                        .filter(
                                x ->
                                        x.getStatus() == SubscriptionStatus.ACTIVE
                                                && x.getEndDate() != null
                                                && x.getEndDate().isAfter(LocalDateTime.now()))
                        .findFirst();
        if (active.isPresent()) return toResponse(active.get(), null);
        UserSubscription sub =
                candidates.stream()
                        .filter(x -> x.getStatus() == SubscriptionStatus.PENDING)
                        .findFirst()
                        .orElseGet(
                                () ->
                                        userSubscriptionRepository.save(
                                                UserSubscription.builder()
                                                        .user(user)
                                                        .servicePackage(plan)
                                                        .startDate(LocalDateTime.now())
                                                        .status(SubscriptionStatus.PENDING)
                                                        .environment(pay2s.environment())
                                                        .amountPaid(plan.getPrice())
                                                        .build()));
        var checkout =
                checkouts.create(
                        "SUBSCRIPTION",
                        sub.getId(),
                        userId,
                        sub.getAmountPaid(),
                        request.getReturnUrl());
        sub.setGatewayTxnRef(checkout.orderId().toString().replace("-", ""));
        userSubscriptionRepository.save(sub);
        cacheInvalidationService.subscriptionChanged(userId);
        return toResponse(sub, checkout);
    }

    @Override
    @Transactional
    public String activateVerifiedPayment(UUID id, String environment, BigDecimal amount) {
        var subscription = userSubscriptionRepository.findByIdForUpdate(id).orElseThrow();
        if (!environment.equals(subscription.getEnvironment())
                || subscription.getAmountPaid() == null
                || amount.compareTo(subscription.getAmountPaid()) != 0) return "REVIEW";
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) return "APPLIED";
        if (subscription.getStatus() != SubscriptionStatus.PENDING) return "REVIEW";
        // Payment duration starts at activation; duplicate callbacks cannot extend it.
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDateTime.now());
        subscription.setEndDate(subscription.getStartDate().plusDays(30));
        userSubscriptionRepository.save(subscription);
        cacheInvalidationService.subscriptionChanged(subscription.getUser().getId());
        notificationService.sendSubscriptionNotification(
                subscription.getUser().getId(),
                id,
                "SUBSCRIPTION_ACTIVE",
                "Gói " + subscription.getServicePackage().getName() + " đã được kích hoạt.");
        return "APPLIED";
    }

    @Override
    @Transactional(readOnly = true)
    public UserSubscriptionResponse confirmPayment(UUID userId, UUID subscriptionId) {
        UserSubscription subscription =
                userSubscriptionRepository
                        .findById(subscriptionId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        // Verify ownership
        if (subscription.getUser() == null || !subscription.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        // Return the server-verified state only. A browser redirect is not proof of payment.
        return toResponse(subscription, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSubscriptionResponse> getMine(UUID userId) {
        return redisCacheService.getListOrSet(
                RedisKeys.userSubscriptions(userId),
                Duration.ofHours(1),
                UserSubscriptionResponse.class,
                () ->
                        userSubscriptionRepository.findByUser_Id(userId).stream()
                                .map(s -> toResponse(s, null))
                                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserSubscriptionResponse> getMinePage(UUID userId, int page, int size) {
        var pageable =
                PageRequests.of(
                        page,
                        size,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.desc("createdAt"),
                                org.springframework.data.domain.Sort.Order.desc("id")));
        java.util.function.Supplier<PageResponse<UserSubscriptionResponse>> source =
                () ->
                        PageResponse.from(
                                userSubscriptionRepository
                                        .findByUser_Id(userId, pageable)
                                        .map(subscription -> toResponse(subscription, null)));
        if (page >= 2) return source.get();
        return redisCacheService.getPageOrSet(
                RedisKeys.page(RedisKeys.userSubscriptions(userId), page, size),
                Duration.ofMinutes(5),
                UserSubscriptionResponse.class,
                source);
    }

    private UserSubscriptionResponse toResponse(
            UserSubscription subscription,
            com.sam.be.modules.payment.service.CheckoutService.Checkout checkout) {
        return UserSubscriptionResponse.builder()
                .id(subscription.getId())
                .packageId(subscription.getServicePackage().getId())
                .status(subscription.getStatus())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .targetProjectId(
                        subscription.getTargetProject() != null
                                ? subscription.getTargetProject().getId()
                                : null)
                .checkout(
                        checkout == null
                                ? checkouts.latest("SUBSCRIPTION", subscription.getId())
                                : checkout)
                .environment(subscription.getEnvironment())
                .entitled(
                        pay2s.environment().equals(subscription.getEnvironment())
                                && subscription.getStatus() == SubscriptionStatus.ACTIVE
                                && (subscription.getEndDate() == null
                                        || subscription.getEndDate().isAfter(LocalDateTime.now())))
                .build();
    }
}
