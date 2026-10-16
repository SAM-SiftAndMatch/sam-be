package com.sam.be.modules.job.dto.response;

import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.modules.job.entity.JobInvitation;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobInvitationResponse(
        UUID id,
        UUID jobId,
        String jobTitle,
        JobStatus jobStatus,
        UUID freelancerId,
        String freelancerName,
        JobInvitation.Source source,
        JobInvitation.Status status,
        String message,
        UUID roomId,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime respondedAt) {}
