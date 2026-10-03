package com.sam.be.modules.chat.service.impl;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.chat.dto.ChatMessageDto;
import com.sam.be.modules.chat.dto.SendMessagePayload;
import com.sam.be.modules.chat.entity.ChatMessage;
import com.sam.be.modules.chat.entity.ChatRoom;
import com.sam.be.modules.chat.repository.ChatMessageRepository;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import com.sam.be.modules.chat.service.ChatService;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ChatRoom getOrCreateRoom(Job job, User freelancer) {
        // Tìm xem 2 người này đã có phòng chat cho Job này chưa
        return chatRoomRepository
                .findByJobIdAndFreelancerId(job.getId(), freelancer.getId())
                .orElseGet(
                        () -> {
                            // Nếu chưa có thì tạo mới tinh
                            ChatRoom newRoom =
                                    ChatRoom.builder()
                                            .job(job)
                                            .client(job.getClient())
                                            .freelancer(freelancer)
                                            .build();
                            return chatRoomRepository.save(newRoom);
                        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessageHistory(UUID roomId, UUID userId) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        // Bảo mật: Chỉ người trong phòng mới được xem lịch sử
        if (!room.getClient().getId().equals(userId)
                && !room.getFreelancer().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        return chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(
                        msg ->
                                ChatMessageDto.builder()
                                        .id(msg.getId())
                                        .roomId(room.getId())
                                        .senderId(msg.getSender().getId())
                                        .senderName(msg.getSender().getFullName())
                                        .content(msg.getContent())
                                        .createdAt(msg.getCreatedAt())
                                        .build())
                .toList();
    }

    @Override
    @Transactional
    public ChatMessageDto saveAndBroadcastMessage(UUID roomId, SendMessagePayload payload) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        User sender =
                userRepository
                        .findById(payload.getSenderId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        // Bảo mật: Thằng gửi có nằm trong phòng này không?
        if (!room.getClient().getId().equals(sender.getId())
                && !room.getFreelancer().getId().equals(sender.getId())) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }

        // Lưu DB
        ChatMessage savedMsg =
                chatMessageRepository.save(
                        ChatMessage.builder()
                                .room(room)
                                .sender(sender)
                                .content(payload.getContent())
                                .build());

        return ChatMessageDto.builder()
                .id(savedMsg.getId())
                .roomId(room.getId())
                .senderId(sender.getId())
                .senderName(sender.getFullName())
                .content(savedMsg.getContent())
                .createdAt(savedMsg.getCreatedAt())
                .build();
    }
}
