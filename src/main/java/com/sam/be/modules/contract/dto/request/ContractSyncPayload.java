package com.sam.be.modules.contract.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ContractSyncPayload {
    private UUID senderId;
    private BigDecimal agreedAmount;
    private Integer revisionLimit;
    private String termsAndConditions;
}