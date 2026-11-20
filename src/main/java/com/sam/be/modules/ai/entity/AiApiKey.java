package com.sam.be.modules.ai.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ai_api_keys")
@Getter
@Setter
public class AiApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String label;

    @Column(name = "key_encrypted", nullable = false, columnDefinition = "TEXT")
    private String keyEncrypted;

    @Column(name = "key_masked", nullable = false, length = 24)
    private String keyMasked;

    @Column(name = "key_fingerprint", nullable = false, length = 64, unique = true)
    private String keyFingerprint;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "fail_count", nullable = false)
    private int failCount = 0;

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
