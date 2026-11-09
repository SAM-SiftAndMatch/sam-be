package com.sam.be.modules.chat.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.response.PageResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.chat.dto.SharedChatAttachmentDto;
import com.sam.be.modules.chat.dto.SharedChatLinksDto;
import com.sam.be.modules.chat.service.ChatResourceService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms/{roomId}")
@PreAuthorize("hasAnyRole('CLIENT', 'FREELANCER')")
public class ChatResourceController {
    private final ChatResourceService resources;

    @GetMapping("/attachments")
    public ApiResponse<PageResponse<SharedChatAttachmentDto>> attachments(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "images") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "18") int size) {
        return ApiResponse.<PageResponse<SharedChatAttachmentDto>>builder()
                .result(
                        resources.attachments(
                                roomId, SecurityUtils.getCurrentUserId(), type, page, size))
                .build();
    }

    @GetMapping("/shared-links")
    public ApiResponse<PageResponse<SharedChatLinksDto>> links(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "18") int size) {
        return ApiResponse.<PageResponse<SharedChatLinksDto>>builder()
                .result(resources.links(roomId, SecurityUtils.getCurrentUserId(), page, size))
                .build();
    }
}
