package com.sam.be.modules.payment.entity;

import com.sam.be.common.constant.enums.PaymentStatus;
import com.sam.be.common.constant.enums.PaymentType;
import com.sam.be.modules.contract.entity.Contract;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "maintenance_order_id")
    private UUID maintenanceOrderId;

    @Column(nullable = false)
    @Builder.Default
    private String provider = "PAY2S";

    @Column(nullable = false)
    @Builder.Default
    private String environment = "SANDBOX";

    @Column(name = "verified_at")
    private java.time.Instant verifiedAt;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    private PaymentType paymentType;

    @Column(name = "installment_no", nullable = false)
    @Builder.Default
    private Integer installmentNo = 1;

    @Column(nullable = false)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "payment_gateway_id")
    private String paymentGatewayId;

    @Column(name = "escrow_held_at")
    private LocalDateTime escrowHeldAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;
}
