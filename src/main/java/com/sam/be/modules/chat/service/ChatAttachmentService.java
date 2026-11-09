package com.sam.be.modules.chat.service;

import com.sam.be.common.exception.*;
import com.sam.be.common.storage.service.StorageService;
import com.sam.be.modules.chat.dto.ChatAttachmentDto;
import com.sam.be.modules.chat.entity.*;
import com.sam.be.modules.chat.repository.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ChatAttachmentService {
    private final ChatAttachmentRepository attachments;
    private final ChatRoomRepository rooms;
    private final StorageService storage;

    public ChatRoom requireRoom(UUID roomId, UUID userId, boolean writing) {
        ChatRoom room =
                rooms.findById(roomId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!room.getClient().getId().equals(userId)
                && !room.getFreelancer().getId().equals(userId))
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        if (writing
                && room.getJob().getSelectedFreelancer() != null
                && !room.getJob()
                        .getSelectedFreelancer()
                        .getId()
                        .equals(room.getFreelancer().getId()))
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        return room;
    }

    @Transactional
    public ChatAttachmentDto upload(UUID roomId, UUID userId, MultipartFile file) {
        ChatRoom room = requireRoom(roomId, userId, true);
        if (file == null || file.isEmpty()) throw ChatFilePolicy.invalid("File trống.");
        if (file.getSize() > ChatFilePolicy.FILE_LIMIT)
            throw ChatFilePolicy.invalid("File tối đa 10MB; ảnh tối đa 5MB.");
        String name = ChatFilePolicy.safeName(file.getOriginalFilename());
        try {
            byte[] bytes = file.getBytes();
            String type = ChatFilePolicy.validate(name, bytes);
            UUID id = UUID.randomUUID();
            String url = storage.uploadFile(bytes, id + "_" + name, "chat/" + roomId);
            ChatAttachment attachment =
                    ChatAttachment.builder()
                            .id(id)
                            .room(room)
                            .ownerId(userId)
                            .fileName(name)
                            .contentType(type)
                            .sizeBytes(bytes.length)
                            .storageUrl(url)
                            .build();
            return dto(attachments.save(attachment));
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Không đọc được file.");
        }
    }

    @Transactional(readOnly = true)
    public ChatAttachment requireReadable(UUID roomId, UUID userId, UUID attachmentId) {
        requireRoom(roomId, userId, false);
        ChatAttachment attachment =
                attachments
                        .findById(attachmentId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!attachment.getRoom().getId().equals(roomId)
                || (attachment.getMessage() == null && !attachment.getOwnerId().equals(userId)))
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        return attachment;
    }

    @Transactional
    public List<ChatAttachment> claim(UUID roomId, UUID userId, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > ChatFilePolicy.MAX_ATTACHMENTS
                || new HashSet<>(ids).size() != ids.size()
                || ids.stream().anyMatch(java.util.Objects::isNull))
            throw ChatFilePolicy.invalid("Mỗi tin nhắn tối đa 5 tệp, không được trùng file.");
        List<ChatAttachment> result = new ArrayList<>();
        long total = 0;
        // Stable lock order avoids deadlocks when concurrent sends claim the same uploads.
        for (UUID id : ids.stream().sorted().toList()) {
            ChatAttachment attachment =
                    attachments
                            .findForUpdate(id)
                            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
            if (!attachment.getOwnerId().equals(userId)
                    || !attachment.getRoom().getId().equals(roomId)
                    || attachment.getMessage() != null)
                throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
            if (attachment.getCreatedAt() != null
                    && attachment.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24)))
                throw ChatFilePolicy.invalid(
                        "File tải lên đã hết hạn gửi. Vui lòng chọn lại file.");
            total += attachment.getSizeBytes();
            result.add(attachment);
        }
        if (total > ChatFilePolicy.MESSAGE_LIMIT)
            throw ChatFilePolicy.invalid("Tổng file trong một tin nhắn tối đa 25MB.");
        return ids.stream()
                .map(
                        id ->
                                result.stream()
                                        .filter(a -> a.getId().equals(id))
                                        .findFirst()
                                        .orElseThrow())
                .toList();
    }

    public static ChatAttachmentDto dto(ChatAttachment attachment) {
        return new ChatAttachmentDto(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getSizeBytes());
    }
}
