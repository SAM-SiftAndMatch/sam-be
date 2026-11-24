package com.sam.be.modules.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessageDto {
    private UUID id;
    private UUID roomId;
    private UUID senderId;
    private String senderName;
    private String content;
    private OffsetDateTime createdAt;
    private UUID clientMessageId;
    private Boolean system;
    @Builder.Default private java.util.List<ChatAttachmentDto> attachments = java.util.List.of();
}
