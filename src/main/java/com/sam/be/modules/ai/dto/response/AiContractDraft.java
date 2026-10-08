package com.sam.be.modules.ai.dto.response;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AiContractDraft {
    private BigDecimal suggestedPrice;
    private String termsAndConditions;
}
