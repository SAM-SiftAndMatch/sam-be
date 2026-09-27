package com.sam.be.modules.contract.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ContractBroadcastData {
    private String type;
    private BigDecimal agreedAmount;
    private Integer revisionLimit;
    private String termsAndConditions;
    private Boolean clientAgreed;
    private Boolean freelancerAgreed;
    private String contractStatus;
}