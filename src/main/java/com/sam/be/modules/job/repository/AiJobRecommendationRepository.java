package com.sam.be.modules.job.repository;

import com.sam.be.modules.job.entity.AiJobRecommendation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiJobRecommendationRepository extends JpaRepository<AiJobRecommendation, UUID> {
    List<AiJobRecommendation> findAllByFreelancerIdOrderByMatchScoreDesc(UUID freelancerId);
}
