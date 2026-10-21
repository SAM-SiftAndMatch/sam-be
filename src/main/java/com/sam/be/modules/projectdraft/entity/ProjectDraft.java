package com.sam.be.modules.projectdraft.entity;

import com.sam.be.common.audit.AbstractAuditEntity;
import com.sam.be.modules.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "project_drafts")
@Getter
@Setter
public class ProjectDraft extends AbstractAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Column(name = "flow_type", nullable = false, length = 16)
    private String flowType;

    @Column(nullable = false, length = 32)
    private String phase;

    @Column(nullable = false, length = 16)
    private String status = "EDITING";

    private String title;

    @Column(columnDefinition = "TEXT")
    private String brief;

    @Column(name = "state_json", columnDefinition = "TEXT", nullable = false)
    private String stateJson = "{}";

    @Column(name = "srs_content", columnDefinition = "TEXT")
    private String srsContent;

    @Column(name = "srs_document_url", length = 500)
    private String srsDocumentUrl;

    @Column(name = "exact_budget_vnd")
    private BigDecimal exactBudgetVnd;

    @Column(name = "duration_months")
    private BigDecimal durationMonths;

    @Column(name = "published_job_id")
    private UUID publishedJobId;

    @Version
    private long version;

    @Column(name = "edit_revision", nullable = false)
    private long editRevision;
}
