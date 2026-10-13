package com.sam.be.modules.payment.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class FreelancerEscrowSummaryResponse {
    private UUID freelancerId;
    private String currency;
    private BigDecimal heldForPayout;
    private BigDecimal lockedSecurityDeposit;
    private BigDecimal releasedToFreelancer;
    private BigDecimal platformFeePending;
    private Integer fundedProjectCount;
}
