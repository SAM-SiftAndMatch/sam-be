package com.sam.be.modules.contract.entity;

import com.sam.be.common.constant.enums.ContractStatus;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.proposal.entity.Proposal;
import com.sam.be.modules.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private Job job;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", unique = true)
    private Proposal proposal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "freelancer_id", nullable = false)
    private User freelancer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Column(name = "agreed_amount", nullable = false)
    private BigDecimal agreedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ContractStatus status = ContractStatus.DRAFT;

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    @Column(name = "client_agreed")
    @Builder.Default
    private Boolean clientAgreed = false;

    @Column(name = "freelancer_agreed")
    @Builder.Default
    private Boolean freelancerAgreed = false;

    // Giá AI đề xuất lúc soạn nháp (gốc để so "đã đổi so với ban đầu")
    @Column(name = "ai_suggested_amount")
    private BigDecimal aiSuggestedAmount;

    // Kết quả AI thẩm định sau ký đôi: NULL (chưa thẩm định) / OK / NEEDS_CONFIRM
    @Column(name = "review_status")
    private String reviewStatus;

    @Column(name = "review_extracted_amount")
    private BigDecimal reviewExtractedAmount;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    // Hai bên bấm "Giữ nguyên bản này" sau khi AI báo lệch
    @Column(name = "client_keep_confirmed")
    @Builder.Default
    private Boolean clientKeepConfirmed = false;

    @Column(name = "freelancer_keep_confirmed")
    @Builder.Default
    private Boolean freelancerKeepConfirmed = false;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
