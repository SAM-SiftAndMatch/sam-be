package com.sam.be.modules.contract.repository;

import com.sam.be.modules.contract.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {
    Optional<Contract> findByJobId(UUID jobId);
    Optional<Contract> findByProposalId(UUID proposalId);
}