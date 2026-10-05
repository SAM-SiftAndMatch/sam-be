package com.sam.be.modules.subscription.service.impl;

import com.sam.be.common.constant.enums.PackageType;
import com.sam.be.common.constant.enums.SubscriptionStatus;
import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.subscription.dto.request.PurchaseSubscriptionRequest;
import com.sam.be.modules.subscription.dto.response.UserSubscriptionResponse;
import com.sam.be.modules.subscription.entity.ServicePackage;
import com.sam.be.modules.subscription.entity.UserSubscription;
import com.sam.be.modules.subscription.repository.ServicePackageRepository;
import com.sam.be.modules.subscription.repository.UserSubscriptionRepository;
import com.sam.be.modules.subscription.service.SubscriptionService;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
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

    private final JobRepository jobRepository;

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

        Job targetProject = null;
        LocalDateTime endDate;
        if (servicePackage.getType() == PackageType.PAY_PER_USE) {
            // Gói lẻ: chỉ CLIENT mua cho đúng 1 project của mình, không hạn ngày.
            if (!SecurityUtils.hasRole(UserRole.CLIENT.name())) {
                throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
            }
            if (request.getProjectId() == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR);
            }
            targetProject =
                    jobRepository
                            .findById(request.getProjectId())
                            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
            if (targetProject.getClient() == null
                    || !targetProject.getClient().getId().equals(userId)) {
                throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
            }
            endDate = null;
        } else {
            // Gói tháng: 30 ngày, không gắn project.
            endDate = LocalDateTime.now().plusDays(30);
        }

        UserSubscription subscription =
                UserSubscription.builder()
                        .user(user)
                        .servicePackage(servicePackage)
                        .startDate(LocalDateTime.now())
                        .endDate(endDate)
                        .targetProject(targetProject)
                        .status(SubscriptionStatus.ACTIVE)
                        .build();
        subscription = userSubscriptionRepository.save(subscription);

        log.info(
                "User {} purchased package {} (type {})",
                userId,
                servicePackage.getId(),
                servicePackage.getType());
        return toResponse(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSubscriptionResponse> getMine(UUID userId) {
        return userSubscriptionRepository.findByUser_Id(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    private UserSubscriptionResponse toResponse(UserSubscription subscription) {
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
                .build();
    }
}
