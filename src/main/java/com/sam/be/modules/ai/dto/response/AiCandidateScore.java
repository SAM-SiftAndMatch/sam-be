package com.sam.be.modules.ai.dto.response;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AiCandidateScore {
    private String candidateId;
    private BigDecimal matchScore;
    private String aiComment;
}