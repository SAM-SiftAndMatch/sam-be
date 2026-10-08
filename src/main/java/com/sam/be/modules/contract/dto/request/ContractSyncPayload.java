package com.sam.be.modules.contract.dto.request;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class ContractSyncPayload {
    private UUID senderId;
    private BigDecimal agreedAmount;
    private String termsAndConditions;
}
