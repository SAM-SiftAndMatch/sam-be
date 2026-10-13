package com.sam.be.modules.workflow.repository;

import com.sam.be.modules.workflow.entity.ProjectDelivery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectDeliveryRepository extends JpaRepository<ProjectDelivery, UUID> {
    List<ProjectDelivery> findAllByJobIdOrderByVersionNumberDesc(UUID jobId);

    Page<ProjectDelivery> findAllByJobId(UUID jobId, Pageable pageable);

    long countByJobId(UUID jobId);

    Optional<ProjectDelivery> findByIdAndJobId(UUID deliveryId, UUID jobId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ProjectDelivery d WHERE d.id = :deliveryId AND d.job.id = :jobId")
    Optional<ProjectDelivery> findByIdAndJobIdForUpdate(
            @Param("deliveryId") UUID deliveryId, @Param("jobId") UUID jobId);
}
