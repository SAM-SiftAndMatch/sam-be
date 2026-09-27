package com.sam.be.modules.chat.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class SendMessagePayload {
    private UUID senderId;
    private String content;
}