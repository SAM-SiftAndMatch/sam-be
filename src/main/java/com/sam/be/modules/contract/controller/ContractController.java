package com.sam.be.modules.contract.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.contract.dto.response.ContractDraftResponse;
import com.sam.be.modules.contract.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
@Tag(name = "Contract Generation", description = "AI based contract draft generation")
public class ContractController {

    private final ContractService contractService;

    @PostMapping("/{roomId}/contracts/ai-draft")
    @Operation(summary = "Generate AI Contract Draft", description = "Generates a draft contract from Job SRS and sets job status to NEGOTIATING")
    public ApiResponse<ContractDraftResponse> generateAiDraft(@PathVariable UUID roomId) {
        ContractDraftResponse response = contractService.createAiDraft(roomId, SecurityUtils.getCurrentUserId());
        return ApiResponse.<ContractDraftResponse>builder().result(response).build();
    }
}