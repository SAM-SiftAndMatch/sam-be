package com.sam.be.modules.feedback;

import com.sam.be.common.constant.enums.UserRole;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FeedbackDtos {
    private FeedbackDtos() {}

    public record CreateInput(
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = 2000) String comment,
            @NotNull UUID requestKey) {}

    public record UpdateInput(
            @NotNull FeedbackStatus status,
            @Size(max = 2000) String adminNote,
            @NotNull @Min(0) Long version) {}

    // User responses deliberately omit the internal note and admin identity.
    public record UserFeedback(
            UUID id,
            int rating,
            String comment,
            FeedbackStatus status,
            Instant createdAt,
            Instant updatedAt) {}

    public record AdminFeedback(
            UUID id,
            int rating,
            String comment,
            FeedbackStatus status,
            Instant createdAt,
            Instant updatedAt,
            UUID userId,
            String userName,
            String userEmail,
            UserRole userRole,
            String adminNote,
            String reviewedByName,
            long version) {}

    public record RatingCount(int rating, long count) {}

    public record Summary(
            long total,
            double averageRating,
            long newCount,
            long inProgressCount,
            long resolvedCount,
            List<RatingCount> ratings) {}
}
