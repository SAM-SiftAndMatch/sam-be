package com.sam.be.modules.subscription.service.impl;

import com.sam.be.common.constant.enums.PackageType;
import com.sam.be.common.constant.enums.SubscriptionStatus;
import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpayClient;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpayProperties;
import com.sam.be.infrastructure.thirdparty.vnpay.VnpaySigner;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    private final JobRepository jobRepository;

    private final VnpayClient vnpayClient;

    private final VnpaySigner vnpaySigner;

    private final VnpayProperties vnpayProperties;

    private final NotificationService notificationService;

    @Override
    @Transactional
    public UserSubscriptionResponse purchase(UUID userId, PurchaseSubscriptionRequest request) {
        if (request.getPackageId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        ServicePackage servicePackage =
                servicePackageRepository
                        .findById(request.getPackageId())
                        .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_AVAILABLE));
        if (!Boolean.TRUE.equals(servicePackage.getIsActive())) {
            throw new ApiException(ErrorCode.PACKAGE_NOT_AVAILABLE);
        }
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        Job resolvedProject = null;
        if (servicePackage.getType() == PackageType.PAY_PER_USE) {
            // Gói lẻ: chỉ CLIENT mua cho đúng 1 project của mình, không hạn ngày.
            if (!SecurityUtils.hasRole(UserRole.CLIENT.name())) {
                throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
            }
            if (request.getProjectId() == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR);
            }
            resolvedProject =
                    jobRepository
                            .findById(request.getProjectId())
                            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
            if (resolvedProject.getClient() == null
                    || !resolvedProject.getClient().getId().equals(userId)) {
                throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
            }
            final UUID resolvedProjectId = resolvedProject.getId();
            boolean alreadyOwned =
                    userSubscriptionRepository.findByUser_Id(userId).stream()
                            .anyMatch(
                                    s ->
                                            s.getStatus() == SubscriptionStatus.ACTIVE
                                                    && s.getServicePackage() != null
                                                    && s.getServicePackage()
                                                            .getId()
                                                            .equals(servicePackage.getId())
                                                    && s.getTargetProject() != null
                                                    && s.getTargetProject()
                                                            .getId()
                                                            .equals(resolvedProjectId));
            if (alreadyOwned) {
                throw new ApiException(ErrorCode.DUPLICATE_RESOURCE);
            }
        }
        final Job targetProject = resolvedProject;

        UserSubscription subscription =
                UserSubscription.builder()
                        .user(user)
                        .servicePackage(servicePackage)
                        .startDate(LocalDateTime.now())
                        .endDate(null)
                        .targetProject(targetProject)
                        .status(SubscriptionStatus.PENDING)
                        .build();
        subscription = userSubscriptionRepository.save(subscription);
        String txnRef = subscription.getId().toString().replace("-", "");
        subscription.setGatewayTxnRef(txnRef);

        String returnUrl = request.getReturnUrl();
        if (returnUrl != null && !returnUrl.isBlank()) {
            if (!vnpayProperties.isReturnUrlAllowed(returnUrl)) {
                throw new ApiException(ErrorCode.INVALID_RETURN_URL);
            }
        } else {
            returnUrl = vnpayProperties.getDefaultReturnUrl();
        }

        BigDecimal price =
                servicePackage.getPrice() != null ? servicePackage.getPrice() : BigDecimal.ZERO;
        long amountVnd = price.multiply(BigDecimal.valueOf(100)).longValueExact();
        String vnpayUrl =
                vnpayClient.buildPaymentUrl(
                        txnRef, amountVnd, "Mua gói " + servicePackage.getName(), null, returnUrl);

        log.info(
                "User {} created PENDING subscription {} (package {})",
                userId,
                subscription.getId(),
                servicePackage.getId());
        return toResponse(subscription, vnpayUrl);
    }

    @Override
    @Transactional
    public Map<String, String> handleVnpayIpn(Map<String, String> params) {
        if (!vnpaySigner.verify(params, vnpayProperties.getHashSecret())) {
            log.warn(
                    "Subscription IPN rejected: invalid signature, txnRef={}",
                    params.get("vnp_TxnRef"));
            return Map.of("RspCode", "97", "Message", "Invalid signature");
        }

        String txnRef = params.get("vnp_TxnRef");
        UserSubscription subscription =
                userSubscriptionRepository.findByGatewayTxnRef(txnRef).orElse(null);
        if (subscription == null) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        BigDecimal price =
                subscription.getServicePackage() != null
                                && subscription.getServicePackage().getPrice() != null
                        ? subscription.getServicePackage().getPrice()
                        : BigDecimal.ZERO;
        long expectedVnd = price.multiply(BigDecimal.valueOf(100)).longValueExact();
        long receivedVnd;
        try {
            receivedVnd = Long.parseLong(params.get("vnp_Amount"));
        } catch (NumberFormatException | NullPointerException e) {
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }
        if (receivedVnd != expectedVnd) {
            log.warn(
                    "Subscription IPN amount mismatch for {}: expected {} got {}",
                    subscription.getId(),
                    expectedVnd,
                    receivedVnd);
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }

        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            return Map.of("RspCode", "02", "Message", "Order already confirmed");
        }

        if ("00".equals(params.get("vnp_ResponseCode"))
                && subscription.getStatus() == SubscriptionStatus.PENDING) {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            if (subscription.getServicePackage() != null
                    && subscription.getServicePackage().getType() != PackageType.PAY_PER_USE) {
                subscription.setEndDate(LocalDateTime.now().plusDays(30));
            }
            subscription = userSubscriptionRepository.save(subscription);

            notificationService.sendSubscriptionNotification(
                    subscription.getUser().getId(),
                    subscription.getId(),
                    "SUBSCRIPTION_ACTIVE",
                    "Gói " + subscription.getServicePackage().getName() + " đã được kích hoạt.");
            log.info("Subscription activated: {}", subscription.getId());
        }
        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSubscriptionResponse> getMine(UUID userId) {
        return userSubscriptionRepository.findByUser_Id(userId).stream()
                .map(s -> toResponse(s, null))
                .toList();
    }

    private UserSubscriptionResponse toResponse(UserSubscription subscription, String vnpayUrl) {
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
                .vnpayUrl(vnpayUrl)
                .build();
    }
}
