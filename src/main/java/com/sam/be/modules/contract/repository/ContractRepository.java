package com.sam.be.modules.contract.repository;

import com.sam.be.modules.contract.entity.Contract;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {
    Optional<Contract> findByJobId(UUID jobId);

    Optional<Contract> findByProposalId(UUID proposalId);

    List<Contract> findAllByJobIdIn(List<UUID> jobIds);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Contract c WHERE c.id = :contractId")
    Optional<Contract> findByIdForSettlement(@Param("contractId") UUID contractId);
}
