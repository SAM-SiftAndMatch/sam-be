package com.sam.be.modules.contract.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContractDraftResponse {
    private UUID contractId;
    private UUID jobId;
    private BigDecimal agreedAmount;
    private String termsAndConditions;
}
