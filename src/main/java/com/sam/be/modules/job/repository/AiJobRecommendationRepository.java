package com.sam.be.modules.job.repository;

import com.sam.be.modules.job.entity.AiJobRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiJobRecommendationRepository extends JpaRepository<AiJobRecommendation, UUID> {
    List<AiJobRecommendation> findAllByJobIdOrderByMatchScoreDesc(UUID jobId);
}