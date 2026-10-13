package com.sam.be.modules.wallet.service;

import com.sam.be.modules.wallet.dto.response.ProjectSettlementResponse;
import com.sam.be.modules.workflow.entity.ProjectDelivery;
import java.util.UUID;

public interface SandboxSettlementService {
    ProjectSettlementResponse settle(UUID contractId, ProjectDelivery delivery);

    ProjectSettlementResponse getByContract(UUID requesterId, UUID contractId);
}
