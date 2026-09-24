package com.sam.be.common.storage.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.storage.dto.request.UploadSrsRequest;
import com.sam.be.common.storage.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Tag(name = "Storage Management", description = "APIs for handling file and document uploads")
public class StorageController {

    private final StorageService storageService;

    @PostMapping("/upload-srs")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Upload SRS Markdown", description = "Converts markdown text to a file on Cloudinary and returns the secure URL")
    public ApiResponse<String> uploadSrs(@Valid @RequestBody UploadSrsRequest request) {
        String secureUrl = storageService.uploadMarkdown(request.getContent(), request.getFileName());
        return ApiResponse.<String>builder().result(secureUrl).build();
    }
}