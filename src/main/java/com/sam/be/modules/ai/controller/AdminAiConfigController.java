package com.sam.be.modules.ai.controller;

import static com.sam.be.modules.ai.dto.AdminAiDtos.*;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.modules.ai.service.AdminAiConfigService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAiConfigController {

    private final AdminAiConfigService service;

    @GetMapping("/runtime")
    public ApiResponse<RuntimeStatus> runtime() {
        return ApiResponse.<RuntimeStatus>builder().result(service.runtimeStatus()).build();
    }

    @GetMapping("/keys")
    public ApiResponse<List<AiKeyResponse>> listKeys() {
        return ApiResponse.<List<AiKeyResponse>>builder().result(service.listKeys()).build();
    }

    @PostMapping("/keys")
    public ApiResponse<AiKeyResponse> createKey(@Valid @RequestBody CreateAiKeyRequest req) {
        return ApiResponse.<AiKeyResponse>builder().result(service.createKey(req)).build();
    }

    @PatchMapping("/keys/{id}")
    public ApiResponse<AiKeyResponse> updateKey(
            @PathVariable Long id, @Valid @RequestBody UpdateAiKeyRequest req) {
        return ApiResponse.<AiKeyResponse>builder().result(service.updateKey(id, req)).build();
    }

    @DeleteMapping("/keys/{id}")
    public ApiResponse<Void> deleteKey(@PathVariable Long id) {
        service.deleteKey(id);
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/keys/{id}/test")
    public ApiResponse<AiTestResult> testSavedKey(
            @PathVariable Long id, @Valid @RequestBody(required = false) TestKeyRequest req) {
        return ApiResponse.<AiTestResult>builder().result(service.testSavedKey(id, req)).build();
    }

    @PostMapping("/keys/test-draft")
    public ApiResponse<AiTestResult> testDraftKey(@Valid @RequestBody TestDraftKeyRequest req) {
        return ApiResponse.<AiTestResult>builder().result(service.testDraftKey(req)).build();
    }

    @GetMapping("/models")
    public ApiResponse<List<AiModelResponse>> listModels() {
        return ApiResponse.<List<AiModelResponse>>builder().result(service.listModels()).build();
    }

    @PostMapping("/models")
    public ApiResponse<AiModelResponse> createModel(@Valid @RequestBody CreateAiModelRequest req) {
        return ApiResponse.<AiModelResponse>builder().result(service.createModel(req)).build();
    }

    @PatchMapping("/models/{id}")
    public ApiResponse<AiModelResponse> updateModel(
            @PathVariable Long id, @Valid @RequestBody UpdateAiModelRequest req) {
        return ApiResponse.<AiModelResponse>builder().result(service.updateModel(id, req)).build();
    }

    @DeleteMapping("/models/{id}")
    public ApiResponse<Void> deleteModel(@PathVariable Long id) {
        service.deleteModel(id);
        return ApiResponse.<Void>builder().build();
    }

    @PostMapping("/models/{id}/test")
    public ApiResponse<AiTestResult> testSavedModel(
            @PathVariable Long id, @Valid @RequestBody(required = false) TestModelRequest req) {
        return ApiResponse.<AiTestResult>builder().result(service.testSavedModel(id, req)).build();
    }

    @PostMapping("/models/test-draft")
    public ApiResponse<AiTestResult> testDraftModel(@Valid @RequestBody TestDraftModelRequest req) {
        return ApiResponse.<AiTestResult>builder().result(service.testDraftModel(req)).build();
    }

    @PostMapping("/models/test-batch")
    public ApiResponse<BatchTestModelsResponse> testModelsBatch(
            @RequestBody(required = false) BatchTestModelsRequest req) {
        return ApiResponse.<BatchTestModelsResponse>builder()
                .result(service.testModelsBatch(req))
                .build();
    }
}
