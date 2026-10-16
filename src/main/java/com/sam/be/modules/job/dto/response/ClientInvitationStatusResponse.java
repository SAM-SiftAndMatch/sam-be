package com.sam.be.modules.job.dto.response;

import com.sam.be.modules.job.entity.JobInvitation;
import java.util.UUID;

public record ClientInvitationStatusResponse(
        UUID jobId,
        UUID freelancerId,
        JobInvitation.Status status) {}
