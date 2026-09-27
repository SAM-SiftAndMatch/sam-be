package com.sam.be.modules.ai.dto.response;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AiContractDraft {
    private BigDecimal suggestedPrice;
    private Integer revisionLimit;
    private String termsAndConditions;
}