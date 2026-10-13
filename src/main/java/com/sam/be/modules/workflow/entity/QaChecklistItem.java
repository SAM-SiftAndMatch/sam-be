package com.sam.be.modules.workflow.entity;

import com.sam.be.common.audit.AbstractAuditEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "qa_checklist_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QaChecklistItem extends AbstractAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qa_plan_id", nullable = false)
    private QaPlan qaPlan;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "requirement_ref", length = 100)
    private String requirementRef;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "test_steps", nullable = false, columnDefinition = "TEXT")
    private String testSteps;

    @Column(name = "expected_result", nullable = false, columnDefinition = "TEXT")
    private String expectedResult;

    @Column(nullable = false, length = 30)
    private String priority;
}
