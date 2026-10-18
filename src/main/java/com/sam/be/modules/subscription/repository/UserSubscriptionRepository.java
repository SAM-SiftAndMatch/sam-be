package com.sam.be.modules.subscription.repository;

import com.sam.be.common.constant.enums.SubscriptionStatus;
import com.sam.be.modules.subscription.entity.UserSubscription;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    List<UserSubscription> findByServicePackage_NameAndStatusAndEndDateAfter(
            String packageName, SubscriptionStatus status, LocalDateTime date);

    List<UserSubscription> findByUser_Id(UUID userId);

    @EntityGraph(attributePaths = {"servicePackage", "targetProject"})
    Page<UserSubscription> findByUser_Id(UUID userId, Pageable pageable);

    Optional<UserSubscription> findByGatewayTxnRef(String gatewayTxnRef);

    @Query("SELECT COUNT(s) > 0 FROM UserSubscription s WHERE s.user.id = :userId "
            + "AND s.servicePackage.id = :packageId AND s.status = :status AND s.endDate > :now")
    boolean hasActivePackage(@Param("userId") UUID userId, @Param("packageId") UUID packageId,
            @Param("status") SubscriptionStatus status, @Param("now") LocalDateTime now);
}
