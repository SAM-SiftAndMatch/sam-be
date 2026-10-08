package com.sam.be.modules.chat.service.impl;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.chat.dto.ChatMessageDto;
import com.sam.be.modules.chat.dto.ChatRoomDto;
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
    private final com.sam.be.modules.contract.repository.ContractRepository contractRepository;

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
                .map(msg -> toDto(room.getId(), msg))
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

        return toDto(room.getId(), savedMsg);
    }

    @Override
    @Transactional
    public ChatMessageDto saveSystemMessage(UUID roomId, String content) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        ChatMessage savedMsg =
                chatMessageRepository.save(
                        ChatMessage.builder().room(room).isSystem(true).content(content).build());

        return toDto(room.getId(), savedMsg);
    }

    private ChatMessageDto toDto(UUID roomId, ChatMessage msg) {
        boolean system = Boolean.TRUE.equals(msg.getIsSystem()) || msg.getSender() == null;
        return ChatMessageDto.builder()
                .id(msg.getId())
                .roomId(roomId)
                .senderId(msg.getSender() != null ? msg.getSender().getId() : null)
                .senderName(
                        msg.getSender() != null ? msg.getSender().getFullName() : "SAM AI")
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .system(system)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomDto> getMyRooms(UUID userId) {
        return chatRoomRepository.findAllByMemberId(userId).stream()
                .map(room -> toRoomDto(room, userId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomDto getRoomDetail(UUID roomId, UUID userId) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        assertMember(room, userId);
        return toRoomDto(room, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomDto getRoomByJob(UUID jobId, UUID userId) {
        ChatRoom room =
                chatRoomRepository
                        .findByJobIdAndMemberId(jobId, userId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return toRoomDto(room, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomDto getRoomByContract(UUID contractId, UUID userId) {
        var contract =
                contractRepository
                        .findById(contractId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        ChatRoom room =
                chatRoomRepository
                        .findByJobIdAndMemberId(contract.getJob().getId(), userId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return toRoomDto(room, userId);
    }

    private void assertMember(ChatRoom room, UUID userId) {
        if (!room.getClient().getId().equals(userId)
                && !room.getFreelancer().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
    }

    private ChatRoomDto toRoomDto(ChatRoom room, UUID userId) {
        String lastMessage = null;
        java.time.LocalDateTime lastMessageAt = null;
        try {
            var last =
                    chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId());
            if (last.isPresent()) {
                lastMessage = last.get().getContent();
                lastMessageAt = last.get().getCreatedAt();
            }
        } catch (Exception ignored) {
        }
        return ChatRoomDto.builder()
                .id(room.getId())
                .jobId(room.getJob().getId())
                .jobTitle(room.getJob().getTitle())
                .clientId(room.getClient().getId())
                .clientName(room.getClient().getFullName())
                .freelancerId(room.getFreelancer().getId())
                .freelancerName(room.getFreelancer().getFullName())
                .lastMessage(lastMessage)
                .lastMessageAt(lastMessageAt)
                .createdAt(room.getCreatedAt())
                .build();
    }
}
