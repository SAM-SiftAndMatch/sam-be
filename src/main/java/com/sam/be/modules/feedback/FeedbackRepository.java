package com.sam.be.modules.feedback;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository
        extends JpaRepository<PlatformFeedback, UUID>, JpaSpecificationExecutor<PlatformFeedback> {
    Optional<PlatformFeedback> findByOwnerIdAndRequestKey(UUID ownerId, UUID requestKey);

    Page<PlatformFeedback> findByOwnerId(UUID ownerId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"owner", "reviewedBy"})
    Page<PlatformFeedback> findAll(
            Specification<PlatformFeedback> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM PlatformFeedback f WHERE f.id = :id")
    Optional<PlatformFeedback> findByIdForUpdate(@Param("id") UUID id);

    interface SummaryRow {
        long getTotal();

        double getAverageRating();

        long getNewCount();

        long getInProgressCount();

        long getResolvedCount();

        long getOneStar();

        long getTwoStars();

        long getThreeStars();

        long getFourStars();

        long getFiveStars();
    }

    @Query(
            value =
                    """
            SELECT COUNT(*) AS total, COALESCE(AVG(rating), 0) AS "averageRating",
                COUNT(*) FILTER (WHERE status = 'NEW') AS "newCount",
                COUNT(*) FILTER (WHERE status = 'IN_PROGRESS') AS "inProgressCount",
                COUNT(*) FILTER (WHERE status = 'RESOLVED') AS "resolvedCount",
                COUNT(*) FILTER (WHERE rating = 1) AS "oneStar",
                COUNT(*) FILTER (WHERE rating = 2) AS "twoStars",
                COUNT(*) FILTER (WHERE rating = 3) AS "threeStars",
                COUNT(*) FILTER (WHERE rating = 4) AS "fourStars",
                COUNT(*) FILTER (WHERE rating = 5) AS "fiveStars"
            FROM platform_feedback
            """,
            nativeQuery = true)
    SummaryRow summary();
}
