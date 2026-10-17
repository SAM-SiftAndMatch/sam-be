package com.sam.be.modules.job.entity;

import com.sam.be.modules.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "job_addon_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobAddonOrder {
    public enum Status { PENDING, PAID, PAID_UNAPPLIED, CANCELLED }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Column(nullable = false) private boolean featured;
    @Column(nullable = false) private boolean urgent;
    @Column(name = "featured_fee", nullable = false) private BigDecimal featuredFee;
    @Column(name = "urgent_fee", nullable = false) private BigDecimal urgentFee;
    @Column(name = "total_fee", nullable = false) private BigDecimal totalFee;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "paid_at") private LocalDateTime paidAt;
}
