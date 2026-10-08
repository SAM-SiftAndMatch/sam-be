package com.sam.be.modules.chat.service;

import com.sam.be.modules.chat.dto.ChatMessageDto;
import com.sam.be.modules.chat.dto.ChatRoomDto;
import com.sam.be.modules.chat.dto.SendMessagePayload;
import com.sam.be.modules.chat.entity.ChatRoom;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.user.entity.User;
import java.util.List;
import java.util.UUID;

public interface ChatService {
    ChatRoom getOrCreateRoom(Job job, User freelancer);

    List<ChatMessageDto> getMessageHistory(UUID roomId, UUID userId);

    ChatMessageDto saveAndBroadcastMessage(UUID roomId, SendMessagePayload payload);

    // Tin hệ thống/AI: không gắn user gửi, FE render bubble riêng (icon + màu khác)
    ChatMessageDto saveSystemMessage(UUID roomId, String content);

    List<ChatRoomDto> getMyRooms(UUID userId);

    ChatRoomDto getRoomDetail(UUID roomId, UUID userId);

    ChatRoomDto getRoomByJob(UUID jobId, UUID userId);

    ChatRoomDto getRoomByContract(UUID contractId, UUID userId);
}
