package com.sam.be.modules.contract.service;

import com.sam.be.modules.contract.dto.request.ContractSignPayload;
import com.sam.be.modules.contract.dto.request.ContractSyncPayload;
import com.sam.be.modules.contract.dto.response.ContractDetailResponse;
import com.sam.be.modules.contract.dto.response.ContractDraftResponse;
import com.sam.be.modules.contract.dto.response.ContractRevisionResponse;
import com.sam.be.modules.contract.entity.Contract;
import java.util.List;
import java.util.UUID;

public interface ContractService {
    ContractDraftResponse createAiDraft(UUID roomId, UUID userId);

    ContractDetailResponse getContractByRoom(UUID roomId, UUID userId);

    List<ContractRevisionResponse> getRevisionsByRoom(UUID roomId, UUID userId);

    void syncContract(UUID contractId, ContractSyncPayload payload);

    void signContract(UUID contractId, ContractSignPayload payload);

    // Hai bên bấm "Giữ nguyên bản này" sau khi AI báo lệch (chỉ khi NEEDS_CONFIRM)
    void confirmKeepContract(UUID contractId, ContractSignPayload payload);

    Contract getContractById(UUID contractId);
}
