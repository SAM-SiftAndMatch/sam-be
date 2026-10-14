package com.sam.be.modules.proposal.repository;

import com.sam.be.common.constant.enums.ProposalStatus;
import com.sam.be.modules.proposal.entity.Proposal;
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
public interface ProposalRepository extends JpaRepository<Proposal, UUID> {
    @Query("SELECT DISTINCT p.job.id FROM Proposal p WHERE p.freelancer.id = :freelancerId")
    List<UUID> findJobIdsByFreelancerId(@Param("freelancerId") UUID freelancerId);

    List<Proposal> findAllByJobId(UUID jobId);

    List<Proposal> findAllByFreelancerId(UUID freelancerId);

    @EntityGraph(attributePaths = {"job", "freelancer"})
    @Query(
            "SELECT p FROM Proposal p WHERE p.job.id = :jobId "
                    + "AND (:status IS NULL OR p.status = :status) ORDER BY p.createdAt DESC, p.id DESC")
    Page<Proposal> findJobPage(
            @Param("jobId") UUID jobId, @Param("status") ProposalStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "freelancer"})
    @Query(
            "SELECT p FROM Proposal p WHERE p.freelancer.id = :freelancerId "
                    + "AND (:status IS NULL OR p.status = :status) ORDER BY p.createdAt DESC, p.id DESC")
    Page<Proposal> findFreelancerPage(
            @Param("freelancerId") UUID freelancerId,
            @Param("status") ProposalStatus status,
            Pageable pageable);

    long countByJobId(UUID jobId);

    Optional<Proposal> findByJobIdAndFreelancerId(UUID jobId, UUID freelancerId);
}
