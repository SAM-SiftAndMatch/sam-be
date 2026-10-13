package com.sam.be.modules.chat.service.impl;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.CursorResponse;
import com.sam.be.common.response.PageRequests;
import com.sam.be.common.response.PageResponse;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
        ChatRoom room = requireMemberRoom(roomId, userId);

        return chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId).stream()
                .map(msg -> toDto(room.getId(), msg))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getRecentMessages(UUID roomId, UUID userId, int limit) {
        ChatRoom room = requireMemberRoom(roomId, userId);
        // Query từ mới đến cũ để DB có thể dừng ở LIMIT; trả về theo thứ tự chat cũ đến mới.
        List<ChatMessage> recent =
                chatMessageRepository.findRecentByRoomId(roomId, PageRequest.of(0, limit));
        return java.util.stream.IntStream.range(0, recent.size())
                .mapToObj(index -> toDto(room.getId(), recent.get(recent.size() - 1 - index)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CursorResponse<ChatMessageDto> getMessagePage(
            UUID roomId, UUID userId, String before, int size) {
        PageRequests.of(0, size, org.springframework.data.domain.Sort.unsorted());
        ChatRoom room = requireMemberRoom(roomId, userId);
        LocalDateTime beforeAt = null;
        UUID beforeId = null;
        if (before != null && !before.isBlank()) {
            try {
                String[] parts = before.split("\\|", -1);
                if (parts.length != 2) throw new IllegalArgumentException("cursor");
                beforeAt = LocalDateTime.parse(parts[0]);
                beforeId = UUID.fromString(parts[1]);
            } catch (IllegalArgumentException ex) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Cursor không hợp lệ");
            }
        }
        List<ChatMessage> fetched =
                beforeAt == null
                        ? chatMessageRepository.findRecentByRoomId(
                                roomId, PageRequest.of(0, size + 1))
                        : chatMessageRepository.findHistoryBefore(
                                roomId, beforeAt, beforeId, PageRequest.of(0, size + 1));
        boolean hasMore = fetched.size() > size;
        List<ChatMessage> window = fetched.subList(0, Math.min(size, fetched.size()));
        String next =
                hasMore && !window.isEmpty()
                        ? window.getLast().getCreatedAt() + "|" + window.getLast().getId()
                        : null;
        List<ChatMessageDto> items =
                java.util.stream.IntStream.range(0, window.size())
                        .mapToObj(
                                index -> toDto(room.getId(), window.get(window.size() - 1 - index)))
                        .toList();
        return new CursorResponse<>(items, next, hasMore);
    }

    private ChatRoom requireMemberRoom(UUID roomId, UUID userId) {
        ChatRoom room =
                chatRoomRepository
                        .findById(roomId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        assertMember(room, userId);
        return room;
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

        room.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(room);

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

        room.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(room);

        return toDto(room.getId(), savedMsg);
    }

    private ChatMessageDto toDto(UUID roomId, ChatMessage msg) {
        boolean system = Boolean.TRUE.equals(msg.getIsSystem()) || msg.getSender() == null;
        return ChatMessageDto.builder()
                .id(msg.getId())
                .roomId(roomId)
                .senderId(msg.getSender() != null ? msg.getSender().getId() : null)
                .senderName(msg.getSender() != null ? msg.getSender().getFullName() : "SAM AI")
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .system(system)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomDto> getMyRooms(UUID userId) {
        List<ChatRoom> rooms = chatRoomRepository.findAllByMemberId(userId);
        return mapRooms(rooms);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatRoomDto> getMyRoomsPage(
            UUID userId, int page, int size, String search) {
        var pageable = PageRequests.of(page, size, org.springframework.data.domain.Sort.unsorted());
        String query =
                search == null || search.isBlank()
                        ? null
                        : "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        Page<ChatRoom> result = chatRoomRepository.findPageByMemberId(userId, query, pageable);
        return new PageResponse<>(
                mapRooms(result.getContent()),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    private List<ChatRoomDto> mapRooms(List<ChatRoom> rooms) {
        if (rooms.isEmpty()) return List.of();
        List<UUID> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        List<UUID> jobIds = rooms.stream().map(room -> room.getJob().getId()).distinct().toList();
        Map<UUID, ChatMessage> latestByRoom =
                chatMessageRepository.findLatestForRooms(roomIds).stream()
                        .collect(
                                Collectors.toMap(
                                        message -> message.getRoom().getId(),
                                        Function.identity(),
                                        (left, right) ->
                                                left.getId().compareTo(right.getId()) >= 0
                                                        ? left
                                                        : right));
        Map<UUID, com.sam.be.common.constant.enums.ContractStatus> statusByJob =
                contractRepository.findAllByJobIdIn(jobIds).stream()
                        .collect(
                                Collectors.toMap(
                                        contract -> contract.getJob().getId(),
                                        com.sam.be.modules.contract.entity.Contract::getStatus));
        return rooms.stream()
                .map(
                        room ->
                                toRoomDto(
                                        room,
                                        latestByRoom.get(room.getId()),
                                        statusByJob.get(room.getJob().getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomDto getRoomDetail(UUID roomId, UUID userId) {
        ChatRoom room =
                chatRoomRepository
                        .findDetailById(roomId)
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
        ChatMessage last =
                chatMessageRepository
                        .findTopByRoomIdOrderByCreatedAtDesc(room.getId())
                        .orElse(null);
        var contractStatus =
                contractRepository
                        .findByJobId(room.getJob().getId())
                        .map(com.sam.be.modules.contract.entity.Contract::getStatus)
                        .orElse(null);
        return toRoomDto(room, last, contractStatus);
    }

    private ChatRoomDto toRoomDto(
            ChatRoom room,
            ChatMessage last,
            com.sam.be.common.constant.enums.ContractStatus contractStatus) {
        return ChatRoomDto.builder()
                .id(room.getId())
                .jobId(room.getJob().getId())
                .jobTitle(room.getJob().getTitle())
                .jobStatus(room.getJob().getStatus())
                .contractStatus(contractStatus)
                .clientId(room.getClient().getId())
                .clientName(room.getClient().getFullName())
                .freelancerId(room.getFreelancer().getId())
                .freelancerName(room.getFreelancer().getFullName())
                .lastMessage(last != null ? last.getContent() : null)
                .lastMessageAt(last != null ? last.getCreatedAt() : null)
                .createdAt(room.getCreatedAt())
                .build();
    }
}
