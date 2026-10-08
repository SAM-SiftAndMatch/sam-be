package com.sam.be.modules.contract.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContractRevisionResponse {
    private UUID id;
    private UUID editorId;
    private String editorName;
    private String editorSide; // CLIENT | FREELANCER
    private BigDecimal agreedAmount;
    private String termsAndConditions;
    private LocalDateTime createdAt;
}
