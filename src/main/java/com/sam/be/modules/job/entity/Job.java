package com.sam.be.modules.job.entity;

import com.sam.be.common.audit.AbstractAuditEntity;
import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.modules.skill.entity.Skill;
import com.sam.be.modules.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "budget_min")
    private BigDecimal budgetMin;

    @Column(name = "budget_max")
    private BigDecimal budgetMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.OPEN;

    private LocalDateTime deadline;

    @Column(name = "srs_document_url", length = 500)
    private String srsDocumentUrl;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(name = "is_urgent_hiring", nullable = false)
    @Builder.Default
    private Boolean isUrgentHiring = false;

    @Column(name = "requires_ai_qa", nullable = false)
    @Builder.Default
    private Boolean requiresAiQa = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "job_skills",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills;
}