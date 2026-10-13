package com.sam.be.modules.wallet.repository;

import com.sam.be.modules.wallet.entity.ProjectSettlement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSettlementRepository extends JpaRepository<ProjectSettlement, UUID> {
    Optional<ProjectSettlement> findByContractId(UUID contractId);
}
