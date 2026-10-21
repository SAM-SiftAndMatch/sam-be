package com.sam.be.modules.projectdraft.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.response.PageResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.JobResponse;
import com.sam.be.modules.projectdraft.dto.*;
import com.sam.be.modules.projectdraft.service.ProjectDraftAiProcessor;
import com.sam.be.modules.projectdraft.service.ProjectDraftAiTurnStore;
import com.sam.be.modules.projectdraft.service.ProjectDraftService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/project-drafts")
@PreAuthorize("hasRole('CLIENT')")
@RequiredArgsConstructor
public class ProjectDraftController {
    private final ProjectDraftService drafts;
    private final ProjectDraftAiTurnStore turnStore;
    private final ProjectDraftAiProcessor aiProcessor;

    @PostMapping
    public ApiResponse<ProjectDraftResponse> create(@Valid @RequestBody CreateProjectDraftRequest request) {
        return ApiResponse.<ProjectDraftResponse>builder()
                .result(drafts.create(SecurityUtils.getCurrentUserId(), request)).build();
    }

    @GetMapping("/page")
    public ApiResponse<PageResponse<ProjectDraftSummaryResponse>> page(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<ProjectDraftSummaryResponse>>builder()
                .result(drafts.page(SecurityUtils.getCurrentUserId(), page, size)).build();
    }

    @GetMapping("/{draftId}")
    public ApiResponse<ProjectDraftResponse> get(@PathVariable UUID draftId) {
        return ApiResponse.<ProjectDraftResponse>builder()
                .result(drafts.get(SecurityUtils.getCurrentUserId(), draftId)).build();
    }

    @PatchMapping("/{draftId}")
    public ApiResponse<ProjectDraftSaveResponse> update(@PathVariable UUID draftId,
            @Valid @RequestBody UpdateProjectDraftRequest request) {
        return ApiResponse.<ProjectDraftSaveResponse>builder()
                .result(drafts.update(SecurityUtils.getCurrentUserId(), draftId, request)).build();
    }

    @DeleteMapping("/{draftId}")
    public ApiResponse<Void> delete(@PathVariable UUID draftId) {
        drafts.delete(SecurityUtils.getCurrentUserId(), draftId);
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/{draftId}/ai-turns")
    public ApiResponse<ProjectDraftAiTurnResponse> chat(@PathVariable UUID draftId,
            @Valid @RequestBody CreateDraftAiTurnRequest request) {
        var turn = turnStore.submit(SecurityUtils.getCurrentUserId(), draftId, request);
        return ApiResponse.<ProjectDraftAiTurnResponse>builder()
                .result(aiProcessor.processNow(turn.getId())).build();
    }

    @PostMapping("/{draftId}/publish")
    public ApiResponse<JobResponse> publish(@PathVariable UUID draftId,
            @Valid @RequestBody JobCreateRequest request) {
        return ApiResponse.<JobResponse>builder()
                .result(drafts.publish(SecurityUtils.getCurrentUserId(), draftId, request)).build();
    }
}
