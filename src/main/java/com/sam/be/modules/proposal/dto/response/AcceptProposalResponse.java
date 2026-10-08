package com.sam.be.modules.proposal.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AcceptProposalResponse {
    private UUID roomId;
}
