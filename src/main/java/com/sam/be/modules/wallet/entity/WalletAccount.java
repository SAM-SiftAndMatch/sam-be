package com.sam.be.modules.wallet.entity;

import com.sam.be.common.audit.AbstractAuditEntity;
import com.sam.be.common.constant.enums.WalletType;
import com.sam.be.modules.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "wallet_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletAccount extends AbstractAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "wallet_key", nullable = false, unique = true, length = 100)
    private String walletKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false, length = 30)
    private WalletType walletType;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "available_balance", nullable = false)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;
}
