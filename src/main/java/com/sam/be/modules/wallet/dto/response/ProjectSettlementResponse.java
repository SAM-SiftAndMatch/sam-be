package com.sam.be.modules.wallet.dto.response;

import com.sam.be.common.constant.enums.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectSettlementResponse {
    private UUID id;
    private UUID contractId;
    private UUID deliveryId;
    private BigDecimal grossAmount;
    private BigDecimal freelancerPayout;
    private BigDecimal platformFee;
    private BigDecimal depositRefund;
    private SettlementStatus status;
    private LocalDateTime completedAt;
    private boolean sandbox;
}
