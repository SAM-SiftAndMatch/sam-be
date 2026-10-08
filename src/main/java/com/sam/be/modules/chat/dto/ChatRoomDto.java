package com.sam.be.modules.chat.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatRoomDto {
    private UUID id;
    private UUID jobId;
    private String jobTitle;
    private UUID clientId;
    private String clientName;
    private UUID freelancerId;
    private String freelancerName;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
