package com.sam.be.modules.job.repository;

import com.sam.be.modules.job.entity.JobAddonPaymentAttempt;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobAddonPaymentAttemptRepository extends JpaRepository<JobAddonPaymentAttempt, UUID> {
    Optional<JobAddonPaymentAttempt> findFirstByOrderIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID orderId, JobAddonPaymentAttempt.Status status, LocalDateTime now);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM JobAddonPaymentAttempt a WHERE a.txnRef = :txnRef")
    Optional<JobAddonPaymentAttempt> findByTxnRefForUpdate(@Param("txnRef") String txnRef);
}
