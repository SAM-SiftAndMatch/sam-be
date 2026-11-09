package com.sam.be.modules.chat.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.annotation.RateLimit;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.common.storage.service.StorageService;
import com.sam.be.modules.chat.dto.ChatAttachmentDto;
import com.sam.be.modules.chat.service.ChatAttachmentService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms/{roomId}/attachments")
@PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER')")
public class ChatAttachmentController {
    private final ChatAttachmentService attachments;
    private final StorageService storage;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(
            type = RateLimit.Type.USER_ID,
            action = "chat_upload",
            limit = 30,
            duration = 60,
            failClosed = true)
    public ApiResponse<ChatAttachmentDto> upload(
            @PathVariable UUID roomId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.<ChatAttachmentDto>builder()
                .result(attachments.upload(roomId, SecurityUtils.getCurrentUserId(), file))
                .build();
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> content(
            @PathVariable UUID roomId,
            @PathVariable UUID attachmentId,
            @RequestParam(defaultValue = "false") boolean download) {
        var attachment =
                attachments.requireReadable(roomId, SecurityUtils.getCurrentUserId(), attachmentId);
        var file = storage.downloadFile(attachment.getStorageUrl());
        boolean inline =
                !download
                        && (attachment.getContentType().startsWith("image/")
                                || "application/pdf".equals(attachment.getContentType()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                                .filename(attachment.getFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(file.getContent());
    }
}
