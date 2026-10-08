package com.sam.be.modules.chat.dto;

import java.time.LocalDateTime;
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
    private LocalDateTime createdAt;
    private Boolean system;
}
