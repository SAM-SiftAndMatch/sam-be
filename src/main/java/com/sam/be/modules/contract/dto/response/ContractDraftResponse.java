package com.sam.be.modules.contract.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ContractDraftResponse {
    private UUID contractId;
    private UUID jobId;
    private BigDecimal agreedAmount;
    private Integer revisionLimit;
    private String termsAndConditions;
}