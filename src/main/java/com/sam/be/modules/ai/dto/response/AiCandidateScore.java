package com.sam.be.modules.ai.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AiCandidateScore {
    private UUID freelancerId;
    private BigDecimal matchScore;
    private String aiComment;
}