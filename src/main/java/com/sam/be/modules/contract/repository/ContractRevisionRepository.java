package com.sam.be.modules.contract.repository;

import com.sam.be.modules.contract.entity.ContractRevision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractRevisionRepository extends JpaRepository<ContractRevision, UUID> {
    List<ContractRevision> findByContractIdOrderByCreatedAtDesc(UUID contractId);

    Optional<ContractRevision> findTopByContractIdOrderByCreatedAtDesc(UUID contractId);
}
