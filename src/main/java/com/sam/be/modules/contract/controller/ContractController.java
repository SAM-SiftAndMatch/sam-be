package com.sam.be.modules.contract.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.contract.dto.response.ContractDetailResponse;
import com.sam.be.modules.contract.dto.response.ContractDraftResponse;
import com.sam.be.modules.contract.dto.response.ContractRevisionResponse;
import com.sam.be.modules.contract.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
@Tag(name = "Contract Generation", description = "AI based contract draft generation")
public class ContractController {

    private final ContractService contractService;

    @PostMapping("/{roomId}/contracts/ai-draft")
    @Operation(
            summary = "Generate AI Contract Draft",
            description =
                    "Generates a draft contract from Job SRS and sets job status to NEGOTIATING")
    public ApiResponse<ContractDraftResponse> generateAiDraft(@PathVariable UUID roomId) {
        ContractDraftResponse response =
                contractService.createAiDraft(roomId, SecurityUtils.getCurrentUserId());
        return ApiResponse.<ContractDraftResponse>builder().result(response).build();
    }

    @GetMapping("/{roomId}/contract")
    @Operation(
            summary = "Get contract of a room",
            description = "Current contract for this chat room (404 if not created yet)")
    public ApiResponse<ContractDetailResponse> getContractByRoom(@PathVariable UUID roomId) {
        ContractDetailResponse response =
                contractService.getContractByRoom(roomId, SecurityUtils.getCurrentUserId());
        return ApiResponse.<ContractDetailResponse>builder().result(response).build();
    }

    @GetMapping("/{roomId}/contract/revisions")
    @Operation(
            summary = "Get contract edit history",
            description = "Snapshots of each edit with editor side, newest first")
    public ApiResponse<java.util.List<ContractRevisionResponse>> getRevisions(
            @PathVariable UUID roomId) {
        java.util.List<ContractRevisionResponse> response =
                contractService.getRevisionsByRoom(roomId, SecurityUtils.getCurrentUserId());
        return ApiResponse.<java.util.List<ContractRevisionResponse>>builder()
                .result(response)
                .build();
    }
}
