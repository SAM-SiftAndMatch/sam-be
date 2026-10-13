package com.sam.be.modules.workflow.entity;

import com.sam.be.common.audit.AbstractAuditEntity;
import com.sam.be.common.constant.enums.QaPlanStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "qa_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QaPlan extends AbstractAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false, unique = true)
    private ProjectDelivery delivery;

    @Column(name = "source_srs", columnDefinition = "TEXT")
    private String sourceSrs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QaPlanStatus status;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
