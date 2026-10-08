package com.sam.be.modules.ai.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private String status;
    private String aiMessage;
    private List<AiQuestion> questions;
    private String srsContent;
    private String currentSrsUrl;
    private String riskLevel;

    // Exact values for job creation (populated when status = COMPLETED)
    private BigDecimal exactBudgetVnd;
    private BigDecimal durationMonths;
}
