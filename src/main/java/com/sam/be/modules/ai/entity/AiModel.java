package com.sam.be.modules.ai.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ai_models")
@Getter
@Setter
public class AiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false, length = 120, unique = true)
    private String modelName;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_test_status", nullable = false, length = 20)
    private AiTestStatus lastTestStatus = AiTestStatus.NEVER;

    @Column(name = "last_test_message", length = 500)
    private String lastTestMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
