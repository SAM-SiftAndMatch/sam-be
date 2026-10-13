package com.sam.be.modules.wallet.dto.response;

import com.sam.be.common.constant.enums.WalletEntryType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletLedgerEntryResponse {
    private UUID id;
    private UUID contractId;
    private WalletEntryType entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private LocalDateTime createdAt;
}
