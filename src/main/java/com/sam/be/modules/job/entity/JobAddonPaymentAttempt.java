package com.sam.be.modules.job.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "job_addon_payment_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobAddonPaymentAttempt {
    public enum Status { PENDING, PAID, FAILED, PAID_DUPLICATE }

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id", nullable = false)
    private JobAddonOrder order;

    @Column(name = "txn_ref", nullable = false, unique = true) private String txnRef;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "payment_url", columnDefinition = "TEXT", nullable = false) private String paymentUrl;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "paid_at") private LocalDateTime paidAt;
}
