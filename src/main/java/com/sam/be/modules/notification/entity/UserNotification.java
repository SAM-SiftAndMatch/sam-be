package com.sam.be.modules.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "user_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotification {
    @Id
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "job_title", length = 500)
    private String jobTitle;

    @Column(name = "match_score", precision = 6, scale = 2)
    private BigDecimal matchScore;

    @Column(name = "invitation_id")
    private UUID invitationId;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "payment_id")
    private UUID paymentId;

    private BigDecimal amount;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "proposal_id")
    private UUID proposalId;

    @Column(name = "recommendation_id")
    private UUID recommendationId;

    @Column(columnDefinition = "TEXT")
    private String message;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder.Default
    @Column(name = "unread_count", nullable = false)
    private int unreadCount = 1;
}
