package com.sam.be.modules.job.dto.response;

import java.math.BigDecimal;

public record JobAddonPriceResponse(
        BigDecimal featuredFee, BigDecimal urgentFee, boolean businessIncluded) {}
