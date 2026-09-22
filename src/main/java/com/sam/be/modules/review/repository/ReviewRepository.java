package com.sam.be.modules.review.repository;

import com.sam.be.modules.review.entity.Review;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByContractId(UUID contractId);

    List<Review> findAllByRevieweeId(UUID revieweeId);
}
