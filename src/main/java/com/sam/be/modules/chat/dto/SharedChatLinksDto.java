package com.sam.be.modules.chat.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SharedChatLinksDto(
        UUID messageId, String senderName, LocalDateTime sentAt, List<String> links) {}
