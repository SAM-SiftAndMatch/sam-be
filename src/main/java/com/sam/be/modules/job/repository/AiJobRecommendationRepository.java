package com.sam.be.modules.job.repository;

import com.sam.be.modules.job.entity.AiJobRecommendation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiJobRecommendationRepository extends JpaRepository<AiJobRecommendation, UUID> {
    @EntityGraph(attributePaths = "freelancer")
    List<AiJobRecommendation> findAllByJobIdOrderByMatchScoreDesc(UUID jobId);

    java.util.Optional<AiJobRecommendation> findByJobIdAndFreelancerId(
            UUID jobId, UUID freelancerId);

    @Query(
            "SELECT DISTINCT r.job.id FROM AiJobRecommendation r WHERE r.freelancer.id = :freelancerId")
    List<UUID> findJobIdsByFreelancerId(@Param("freelancerId") UUID freelancerId);
}
