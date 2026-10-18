package com.sam.be.modules.job.dto.response;

import com.sam.be.common.constant.enums.JobStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ClientDashboardProjectResponse(
        UUID id,
        String title,
        JobStatus status,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        LocalDateTime updatedAt,
        String freelancerName) {}
