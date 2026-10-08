package com.sam.be.modules.contract.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContractBroadcastData {
    private String type;
    private BigDecimal agreedAmount;
    private String termsAndConditions;
    private Boolean clientAgreed;
    private Boolean freelancerAgreed;
    private String contractStatus;
    // AI thẩm định sau ký đôi: NULL / OK / NEEDS_CONFIRM
    private String reviewStatus;
    private String reviewNote;
    private Boolean clientKeepConfirmed;
    private Boolean freelancerKeepConfirmed;
}
