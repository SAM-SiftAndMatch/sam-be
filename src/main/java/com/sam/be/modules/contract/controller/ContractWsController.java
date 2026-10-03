package com.sam.be.modules.contract.controller;

import com.sam.be.modules.contract.dto.request.ContractSignPayload;
import com.sam.be.modules.contract.dto.request.ContractSyncPayload;
import com.sam.be.modules.contract.service.ContractService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ContractWsController {

    private final ContractService contractService;

    @MessageMapping("/contracts/{contractId}/sync")
    public void syncContract(
            @DestinationVariable UUID contractId, @Payload ContractSyncPayload payload) {
        contractService.syncContract(contractId, payload);
    }

    @MessageMapping("/contracts/{contractId}/sign")
    public void signContract(
            @DestinationVariable UUID contractId, @Payload ContractSignPayload payload) {
        contractService.signContract(contractId, payload);
    }
}
