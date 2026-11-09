package com.sam.be.modules.chat.service;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.PageRequests;
import com.sam.be.common.response.PageResponse;
import com.sam.be.modules.chat.dto.*;
import com.sam.be.modules.chat.repository.*;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatResourceService {
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)[^\\s<>\"'`]+");
    private final ChatAttachmentService access;
    private final ChatAttachmentRepository attachments;
    private final ChatMessageRepository messages;

    @Transactional(readOnly = true)
    public PageResponse<SharedChatAttachmentDto> attachments(
            UUID roomId, UUID userId, String type, int page, int size) {
        access.requireRoom(roomId, userId, false);
        if (type == null || !Set.of("images", "files").contains(type))
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Loại tài nguyên không hợp lệ.");
        var pageable =
                PageRequests.of(
                        page,
                        size,
                        Sort.by(Sort.Order.desc("message.createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(
                attachments
                        .findShared(roomId, "images".equals(type), pageable)
                        .map(
                                a ->
                                        new SharedChatAttachmentDto(
                                                a.getId(),
                                                a.getFileName(),
                                                a.getContentType(),
                                                a.getSizeBytes(),
                                                a.getMessage().getId(),
                                                a.getMessage().getSender().getFullName(),
                                                a.getMessage().getCreatedAt())));
    }

    @Transactional(readOnly = true)
    public PageResponse<SharedChatLinksDto> links(UUID roomId, UUID userId, int page, int size) {
        access.requireRoom(roomId, userId, false);
        var pageable =
                PageRequests.of(
                        page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(
                messages.findSharedLinks(roomId, pageable)
                        .map(
                                m ->
                                        new SharedChatLinksDto(
                                                m.getId(),
                                                m.getSender().getFullName(),
                                                m.getCreatedAt(),
                                                extractLinks(m.getContent()))));
    }

    public static List<String> extractLinks(String content) {
        if (content == null || content.isBlank()) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        var matcher = URL.matcher(content);
        while (matcher.find()) {
            String value = matcher.group();
            while (!value.isEmpty()) {
                char last = value.charAt(value.length() - 1);
                boolean unmatched =
                        (last == ')' && count(value, ')') > count(value, '('))
                                || (last == ']' && count(value, ']') > count(value, '['))
                                || (last == '}' && count(value, '}') > count(value, '{'));
                if (unmatched || ".,;!?".indexOf(last) >= 0)
                    value = value.substring(0, value.length() - 1);
                else break;
            }
            if (value.regionMatches(true, 0, "www.", 0, 4)) value = "https://" + value;
            try {
                URI uri = URI.create(value);
                if (("https".equalsIgnoreCase(uri.getScheme())
                                || "http".equalsIgnoreCase(uri.getScheme()))
                        && uri.getHost() != null
                        && uri.getRawUserInfo() == null
                        && uri.getPort() <= 65535) result.add(value);
            } catch (IllegalArgumentException ignored) {
                /* Ignore malformed URLs in old messages. */
            }
        }
        return List.copyOf(result);
    }

    private static long count(String value, char character) {
        return value.chars().filter(c -> c == character).count();
    }
}
