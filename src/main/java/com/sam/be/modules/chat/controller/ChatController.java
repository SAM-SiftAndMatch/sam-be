package com.sam.be.modules.chat.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.response.CursorResponse;
import com.sam.be.common.response.PageResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.chat.dto.ChatMessageDto;
import com.sam.be.modules.chat.dto.ChatRoomDto;
import com.sam.be.modules.chat.dto.SendMessagePayload;
import com.sam.be.modules.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat Management", description = "APIs and WebSocket for Freelancer-Client negotiation")
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/rooms/{roomId}/messages/page")
    @Operation(summary = "Get a cursor page of chat history")
    public ApiResponse<CursorResponse<ChatMessageDto>> getMessagePage(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String before) {
        return ApiResponse.<CursorResponse<ChatMessageDto>>builder()
                .result(
                        chatService.getMessagePage(
                                roomId, SecurityUtils.getCurrentUserId(), before, size))
                .build();
    }

    @GetMapping("/rooms/page")
    @Operation(summary = "Get a page of my chat rooms")
    public ApiResponse<PageResponse<ChatRoomDto>> getMyRoomsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        return ApiResponse.<PageResponse<ChatRoomDto>>builder()
                .result(
                        chatService.getMyRoomsPage(
                                SecurityUtils.getCurrentUserId(), page, size, search))
                .build();
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount() {
        return ApiResponse.<Long>builder()
                .result(chatService.getUnreadMessageCount(SecurityUtils.getCurrentUserId()))
                .build();
    }

    @org.springframework.web.bind.annotation.PostMapping("/rooms/{roomId}/read")
    public ApiResponse<Void> markRoomRead(@PathVariable UUID roomId) {
        chatService.markRoomRead(roomId, SecurityUtils.getCurrentUserId());
        return ApiResponse.<Void>builder().build();
    }

    // Chi tiết 1 phòng (để mở đúng tên job + đối phương)
    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Get chat room detail", description = "Room detail with job and members")
    public ApiResponse<ChatRoomDto> getRoomDetail(@PathVariable UUID roomId) {
        ChatRoomDto response = chatService.getRoomDetail(roomId, SecurityUtils.getCurrentUserId());
        return ApiResponse.<ChatRoomDto>builder().result(response).build();
    }

    // Tìm phòng theo job (FE đang ở trang job vừa Accept mà chỉ có jobId)
    @GetMapping("/by-job/{jobId}")
    @Operation(
            summary = "Get chat room by job",
            description = "Resolve the chat room of current user for a given job")
    public ApiResponse<ChatRoomDto> getRoomByJob(@PathVariable UUID jobId) {
        ChatRoomDto response = chatService.getRoomByJob(jobId, SecurityUtils.getCurrentUserId());
        return ApiResponse.<ChatRoomDto>builder().result(response).build();
    }

    @GetMapping("/by-contract/{contractId}")
    @Operation(summary = "Get chat room by contract")
    public ApiResponse<ChatRoomDto> getRoomByContract(@PathVariable UUID contractId) {
        ChatRoomDto response =
                chatService.getRoomByContract(contractId, SecurityUtils.getCurrentUserId());
        return ApiResponse.<ChatRoomDto>builder().result(response).build();
    }

    // WEBSOCKET ENDPOINT: FE bắn tin nhắn vào đây
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
