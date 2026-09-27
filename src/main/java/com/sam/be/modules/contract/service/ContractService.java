package com.sam.be.modules.contract.service;

import com.sam.be.modules.contract.dto.request.ContractSignPayload;
import com.sam.be.modules.contract.dto.request.ContractSyncPayload;
import com.sam.be.modules.contract.dto.response.ContractDraftResponse;
import java.util.UUID;

public interface ContractService {
    ContractDraftResponse createAiDraft(UUID roomId, UUID userId);
    void syncContract(UUID contractId, ContractSyncPayload payload);
    void signContract(UUID contractId, ContractSignPayload payload);
}