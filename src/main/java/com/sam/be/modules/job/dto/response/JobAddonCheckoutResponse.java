package com.sam.be.modules.job.dto.response;

import java.util.UUID;

public record JobAddonCheckoutResponse(UUID orderId, String vnpayUrl) {}
