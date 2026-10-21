package com.sam.be.modules.projectdraft.dto;

import com.sam.be.modules.ai.dto.response.AiChatResponse;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectDraftAiTurnResponse(
        UUID id,
        UUID clientMessageId,
        String phase,
        String userMessage,
        String status,
        AiChatResponse aiResponse,
        LocalDateTime createdAt) {}
