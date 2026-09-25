package com.sam.be.modules.subscription.repository;

import com.sam.be.common.constant.enums.SubscriptionStatus;
import com.sam.be.modules.subscription.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    List<UserSubscription> findByServicePackage_NameAndStatusAndEndDateAfter(
            String packageName,
            SubscriptionStatus status,
            LocalDateTime date
    );
}