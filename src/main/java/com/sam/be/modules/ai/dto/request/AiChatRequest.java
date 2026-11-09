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
    @NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private String sessionId;

    @NotBlank
    @jakarta.validation.constraints.Size(max = 10000)
    private String userMessage;

    /** Only populated from the owned server-side draft, never from incoming JSON. */
    @com.fasterxml.jackson.annotation.JsonIgnore private String projectContext;

    @jakarta.validation.constraints.Size(max = 60000)
    private String currentSrsContent;

    /**
     * Toàn bộ lịch sử hội thoại risk-chat (role: "user"|"assistant", content) để AI nhớ ngữ cảnh
     */
    @jakarta.validation.constraints.Size(max = 24)
    private List<Map<String, String>> chatHistory;
}
