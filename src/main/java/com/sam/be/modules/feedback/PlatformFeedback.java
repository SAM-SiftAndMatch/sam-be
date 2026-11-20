package com.sam.be.modules.feedback;

import com.sam.be.modules.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "platform_feedback")
@Getter
@Setter
public class PlatformFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;

    @Column(name = "request_key", nullable = false, updatable = false)
    private UUID requestKey;

    @Column(nullable = false, updatable = false)
    private int rating;

    @Column(nullable = false, length = 2000, updatable = false)
    private String comment = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FeedbackStatus status = FeedbackStatus.NEW;

    @Column(name = "admin_note", nullable = false, length = 2000)
    private String adminNote = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;
}
