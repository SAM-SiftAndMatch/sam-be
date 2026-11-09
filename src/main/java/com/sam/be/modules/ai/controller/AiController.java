package com.sam.be.modules.ai.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.modules.ai.dto.request.AiChatRequest;
import com.sam.be.modules.ai.dto.response.AiChatResponse;
import com.sam.be.modules.ai.dto.response.AiQuestion;
import com.sam.be.modules.ai.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Assistant", description = "AI services for BA phase and Risk Assessment phase")
public class AiController {

    private final AiService aiService;

    @GetMapping("/base-questions")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Get base questions")
    public ApiResponse<List<AiQuestion>> getBaseQuestions() {
        List<AiQuestion> response = aiService.getBaseQuestions();
        return ApiResponse.<List<AiQuestion>>builder().result(response).build();
    }

    @com.sam.be.common.security.annotation.RateLimit(
            type = com.sam.be.common.security.annotation.RateLimit.Type.USER_ID,
            action = "ai_project_chat",
            limit = 10,
            duration = 60,
            failClosed = true)
    @PostMapping("/ba-chat")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Chat with AI BA to generate SRS")
    public ApiResponse<AiChatResponse> chatWithAiBa(@Valid @RequestBody AiChatRequest request) {
        AiChatResponse response = aiService.chatWithAiBa(request);
        return ApiResponse.<AiChatResponse>builder().result(response).build();
    }

    @com.sam.be.common.security.annotation.RateLimit(
            type = com.sam.be.common.security.annotation.RateLimit.Type.USER_ID,
            action = "ai_project_chat",
            limit = 10,
            duration = 60,
            failClosed = true)
    @PostMapping("/risk-chat")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Chat with AI Risk Assessor to negotiate SRS/Budget/Deadline")
    public ApiResponse<AiChatResponse> chatWithAiRisk(@Valid @RequestBody AiChatRequest request) {
        AiChatResponse response = aiService.chatWithAiRisk(request);
        return ApiResponse.<AiChatResponse>builder().result(response).build();
    }
}
