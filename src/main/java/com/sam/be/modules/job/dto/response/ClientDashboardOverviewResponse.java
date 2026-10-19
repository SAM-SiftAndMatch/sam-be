package com.sam.be.modules.job.dto.response;

import java.util.List;

public record ClientDashboardOverviewResponse(
        long totalPosted,
        long inProgress,
        long completed,
        List<ClientDashboardProjectResponse> recentProjects) {}
