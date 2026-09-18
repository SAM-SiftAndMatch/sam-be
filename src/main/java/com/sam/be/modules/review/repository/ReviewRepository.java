package com.sam.be.modules.review.repository;

import com.sam.be.modules.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByContractId(UUID contractId);
    List<Review> findAllByRevieweeId(UUID revieweeId);
}