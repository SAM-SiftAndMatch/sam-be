package com.sam.be.modules.ai.dto.response;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class AiCandidateScore {
    private String candidateId;
    private BigDecimal matchScore;
    private String aiComment;
}
