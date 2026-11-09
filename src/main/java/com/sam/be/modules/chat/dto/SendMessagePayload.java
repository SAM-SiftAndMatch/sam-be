package com.sam.be.modules.chat.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class SendMessagePayload {
    private UUID senderId;
    private String content;
    private java.util.List<UUID> attachmentIds;
    private UUID clientMessageId;
}
