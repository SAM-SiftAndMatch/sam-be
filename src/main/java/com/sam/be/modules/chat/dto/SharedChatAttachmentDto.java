package com.sam.be.modules.chat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SharedChatAttachmentDto(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        UUID messageId,
        String senderName,
        LocalDateTime sentAt) {}
