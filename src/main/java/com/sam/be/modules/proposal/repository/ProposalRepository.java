package com.sam.be.modules.proposal.repository;

import com.sam.be.modules.proposal.entity.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, UUID> {
    List<Proposal> findAllByJobId(UUID jobId);
    List<Proposal> findAllByFreelancerId(UUID freelancerId);
}