package com.sam.be.modules.contract.service;

import com.sam.be.modules.contract.dto.response.ContractDraftResponse;
import java.util.UUID;

public interface ContractService {
    ContractDraftResponse createAiDraft(UUID roomId, UUID userId);
}