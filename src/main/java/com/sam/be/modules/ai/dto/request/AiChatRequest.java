package com.sam.be.modules.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {
    @NotBlank
    private String sessionId;

    @NotBlank
    private String userMessage;

    private String currentSrsContent;
}