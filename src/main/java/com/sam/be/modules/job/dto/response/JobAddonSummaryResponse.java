package com.sam.be.modules.job.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record JobAddonSummaryResponse(
        UUID orderId,
        String orderStatus,
        boolean featuredActive,
        boolean urgentActive,
        boolean featuredPending,
        boolean urgentPending,
        BigDecimal featuredFee,
        BigDecimal urgentFee,
        BigDecimal totalDue,
        boolean businessIncluded) {}
