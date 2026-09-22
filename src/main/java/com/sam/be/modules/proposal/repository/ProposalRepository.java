package com.sam.be.modules.proposal.repository;

import com.sam.be.modules.proposal.entity.Proposal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, UUID> {
    List<Proposal> findAllByJobId(UUID jobId);

    List<Proposal> findAllByFreelancerId(UUID freelancerId);
}
