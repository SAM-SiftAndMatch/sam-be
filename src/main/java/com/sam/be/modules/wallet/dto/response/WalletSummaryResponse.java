package com.sam.be.modules.wallet.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletSummaryResponse {
    private UUID walletId;
    private String currency;
    private BigDecimal availableBalance;
    private boolean sandbox;
}
