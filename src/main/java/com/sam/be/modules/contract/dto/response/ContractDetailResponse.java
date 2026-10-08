package com.sam.be.modules.contract.dto.response;

import com.sam.be.common.constant.enums.ContractStatus;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContractDetailResponse {
    private UUID id;
    private UUID jobId;
    private String jobTitle;
    private BigDecimal agreedAmount;
    private String termsAndConditions;
    private ContractStatus status;
    private Boolean clientAgreed;
    private Boolean freelancerAgreed;
    private String reviewStatus;
    private String reviewNote;
    private Boolean clientKeepConfirmed;
    private Boolean freelancerKeepConfirmed;
    private UUID clientId;
    private String clientName;
    private UUID freelancerId;
    private String freelancerName;
}
