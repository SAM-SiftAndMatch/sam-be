package com.sam.be.modules.job.repository;

import com.sam.be.modules.job.entity.JobAddonOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobAddonOrderRepository extends JpaRepository<JobAddonOrder, UUID> {
    Optional<JobAddonOrder> findFirstByJobIdAndStatusOrderByCreatedAtDesc(
            UUID jobId, JobAddonOrder.Status status);

    Optional<JobAddonOrder> findFirstByJobIdOrderByCreatedAtDesc(UUID jobId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM JobAddonOrder o WHERE o.id = :id")
    Optional<JobAddonOrder> findByIdForUpdate(@Param("id") UUID id);
}
