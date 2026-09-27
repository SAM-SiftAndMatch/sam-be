package com.sam.be.modules.chat.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.chat.dto.ChatMessageDto;
import com.sam.be.modules.chat.dto.SendMessagePayload;
import com.sam.be.modules.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat Management", description = "APIs and WebSocket for Freelancer-Client negotiation")
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // 1. REST API: FE gọi lúc vừa mở màn hình để load lịch sử
    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Get chat history", description = "Retrieve all previous messages for a specific room")
    public ApiResponse<List<ChatMessageDto>> getChatHistory(@PathVariable UUID roomId) {
        List<ChatMessageDto> response = chatService.getMessageHistory(roomId, SecurityUtils.getCurrentUserId());
        return ApiResponse.<List<ChatMessageDto>>builder().result(response).build();
    }

    // 2. WEBSOCKET ENDPOINT: FE bắn tin nhắn vào đây
    // URL phía FE sẽ gọi: /app/chat/{roomId}/send
    @MessageMapping("/chat/{roomId}/send")
    public void sendMessage(@DestinationVariable UUID roomId, @Payload SendMessagePayload payload) {
        // Lưu tin nhắn vào DB
        ChatMessageDto savedMessage = chatService.saveAndBroadcastMessage(roomId, payload);

        // Phát thanh tin nhắn vừa lưu xuống kênh của phòng này
        String destination = "/topic/chat/" + roomId;
        messagingTemplate.convertAndSend(destination, savedMessage);
    }
}