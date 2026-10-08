package com.sam.be.modules.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {
    @NotBlank private String sessionId;

    @NotBlank private String userMessage;

    private String currentSrsContent;

    /**
     * Toàn bộ lịch sử hội thoại risk-chat (role: "user"|"assistant", content) để AI nhớ ngữ cảnh
     */
    private List<Map<String, String>> chatHistory;
}
