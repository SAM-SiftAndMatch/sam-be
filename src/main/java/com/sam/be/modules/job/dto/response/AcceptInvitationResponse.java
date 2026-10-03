package com.sam.be.modules.job.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AcceptInvitationResponse {
    private UUID roomId;
}
