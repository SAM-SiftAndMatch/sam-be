package com.sam.be.modules.job.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.job.dto.request.JobAddonCheckoutRequest;
import com.sam.be.modules.job.dto.request.JobAddonSelectionRequest;
import com.sam.be.modules.job.dto.response.JobAddonPriceResponse;
import com.sam.be.modules.job.dto.response.JobAddonSummaryResponse;
import com.sam.be.modules.job.service.JobAddonService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENT')")
public class JobAddonController {
    private final JobAddonService addonService;

    @GetMapping("/addon-prices")
    public ApiResponse<JobAddonPriceResponse> prices() {
        return ApiResponse.<JobAddonPriceResponse>builder()
                .result(addonService.prices(SecurityUtils.getCurrentUserId()))
                .build();
    }

    @GetMapping("/{jobId}/addons")
    public ApiResponse<JobAddonSummaryResponse> summary(@PathVariable UUID jobId) {
        return ApiResponse.<JobAddonSummaryResponse>builder()
                .result(addonService.summary(SecurityUtils.getCurrentUserId(), jobId))
                .build();
    }

    @PostMapping("/{jobId}/addons")
    public ApiResponse<JobAddonSummaryResponse> select(
            @PathVariable UUID jobId, @RequestBody JobAddonSelectionRequest request) {
        return ApiResponse.<JobAddonSummaryResponse>builder()
                .result(
                        addonService.select(
                                SecurityUtils.getCurrentUserId(),
                                jobId,
                                request.featured(),
                                request.urgent()))
                .build();
    }

    @PostMapping("/{jobId}/addons/checkout")
    @com.sam.be.common.security.annotation.RateLimit(
            type = com.sam.be.common.security.annotation.RateLimit.Type.USER_ID,
            action = "addon_checkout",
            limit = 5,
            duration = 60,
            failClosed = true)
    public ApiResponse<com.sam.be.modules.payment.service.CheckoutService.Checkout> checkout(
            @PathVariable UUID jobId, @RequestBody JobAddonCheckoutRequest request) {
        return ApiResponse.<com.sam.be.modules.payment.service.CheckoutService.Checkout>builder()
                .result(
                        addonService.checkout(
                                SecurityUtils.getCurrentUserId(), jobId, request.returnUrl()))
                .build();
    }
}
