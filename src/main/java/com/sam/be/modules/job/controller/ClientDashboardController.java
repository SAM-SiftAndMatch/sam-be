package com.sam.be.modules.job.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.job.dto.response.ClientDashboardOverviewResponse;
import com.sam.be.modules.job.service.ClientDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs/client/me/overview")
@RequiredArgsConstructor
public class ClientDashboardController {
    private final ClientDashboardService service;

    @GetMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ApiResponse<ClientDashboardOverviewResponse> overview() {
        return ApiResponse.<ClientDashboardOverviewResponse>builder()
                .result(service.getOverview(SecurityUtils.getCurrentUserId()))
                .build();
    }
}
