package com.sam.be.modules.job.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class AcceptInvitationResponse {
    private UUID roomId;
}