package com.sam.be.modules.ai.dto.response;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AiAmountVerification {
    private BigDecimal extractedAmount;
    private Boolean matches;
    private String note;
}
