package com.sam.be.modules.projectdraft.entity;

import com.sam.be.common.audit.AbstractAuditEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "project_draft_ai_turns")
@Getter
@Setter
public class ProjectDraftAiTurn extends AbstractAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    private ProjectDraft draft;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Column(nullable = false, length = 8)
    private String phase;

    @Column(name = "user_message", nullable = false, columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "ai_response_json", columnDefinition = "TEXT")
    private String aiResponseJson;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(nullable = false)
    private int attempts;
}
