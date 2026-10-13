package com.sam.be.modules.workflow.repository;

import com.sam.be.modules.workflow.entity.QaPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaPlanRepository extends JpaRepository<QaPlan, UUID> {
    Optional<QaPlan> findByDeliveryId(UUID deliveryId);
}
