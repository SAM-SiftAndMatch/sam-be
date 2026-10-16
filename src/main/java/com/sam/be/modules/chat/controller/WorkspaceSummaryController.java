package com.sam.be.modules.chat.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.chat.dto.WorkspaceSummaryDto;
import com.sam.be.modules.chat.service.WorkspaceSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "Workspace", description = "Lightweight workspace header for project participants")
public class WorkspaceSummaryController {

    private final WorkspaceSummaryService workspaceSummaryService;

    @GetMapping("/{roomId}/summary")
    @Operation(summary = "Get only the fields needed by the workspace header")
    public ApiResponse<WorkspaceSummaryDto> getSummary(@PathVariable UUID roomId) {
        return ApiResponse.<WorkspaceSummaryDto>builder()
                .result(
                        workspaceSummaryService.getSummary(
                                roomId, SecurityUtils.getCurrentUserId()))
                .build();
    }
}
