package com.sam.be.modules.workflow.repository;

import com.sam.be.modules.workflow.entity.ProjectDelivery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ProjectDeliveryRepository extends JpaRepository<ProjectDelivery, UUID> {
    List<ProjectDelivery> findAllByJobIdOrderByVersionNumberDesc(UUID jobId);

    long countByJobId(UUID jobId);

    java.util.Optional<ProjectDelivery> findByIdAndJobId(UUID deliveryId, UUID jobId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<ProjectDelivery> findByIdAndJobIdForUpdate(UUID deliveryId, UUID jobId);
}
