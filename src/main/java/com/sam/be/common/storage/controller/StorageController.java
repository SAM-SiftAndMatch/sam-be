package com.sam.be.common.storage.controller;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.storage.dto.request.UploadSrsRequest;
import com.sam.be.common.storage.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Tag(name = "Storage Management", description = "APIs for handling file and document uploads")
public class StorageController {

    private final StorageService storageService;

    @PostMapping("/upload-srs")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Upload SRS Markdown",
            description =
                    "Converts markdown text to a file on Cloudinary and returns the secure URL")
    public ApiResponse<String> uploadSrs(@Valid @RequestBody UploadSrsRequest request) {
        String secureUrl =
                storageService.uploadMarkdown(request.getContent(), request.getFileName());
        return ApiResponse.<String>builder().result(secureUrl).build();
    }

    @PostMapping(value = "/upload-file", consumes = "multipart/form-data")    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Upload a file (PDF/DOC/DOCX, max 10MB)",
            description = "Uploads a proposal attachment to Cloudinary and returns the secure URL")
    public ApiResponse<String> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "File trống.");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "File tối đa 10MB.");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String lower = name.toLowerCase();
        if (!lower.endsWith(".pdf") && !lower.endsWith(".doc") && !lower.endsWith(".docx")) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Chỉ nhận file PDF/DOC/DOCX.");
        }
        try {
            String url = storageService.uploadFile(file.getBytes(), name, "proposals");
            return ApiResponse.<String>builder().result(url).build();
        } catch (java.io.IOException e) {
            throw new ApiException(ErrorCode.UNEXPECTED_ERROR);
        }
    }

    @GetMapping("/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Download a file through backend",
            description =
                    "Fetches a Cloudinary file server-side (avoids direct-link ACL errors) and returns it as an attachment")
    public org.springframework.http.ResponseEntity<byte[]> download(
            @RequestParam("url") String url) {
        com.sam.be.common.storage.dto.response.FileData data = storageService.downloadFile(url);
        return org.springframework.http.ResponseEntity.ok()
                .header(
                        org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''"
                                + java.net.URLEncoder.encode(
                                        data.getFileName(), java.nio.charset.StandardCharsets.UTF_8)
                                        .replace("+", "%20"))
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, data.getContentType())
                .body(data.getContent());
    }
}
