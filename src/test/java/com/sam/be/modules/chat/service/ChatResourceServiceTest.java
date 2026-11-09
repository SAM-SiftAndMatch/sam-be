package com.sam.be.modules.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.storage.service.StorageService;
import com.sam.be.modules.chat.entity.*;
import com.sam.be.modules.chat.repository.*;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.user.entity.User;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.*;

class ChatResourceServiceTest {
    private final ChatAttachmentRepository attachments = mock(ChatAttachmentRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    private final ChatResourceService service =
            new ChatResourceService(
                    new ChatAttachmentService(attachments, rooms, mock(StorageService.class)),
                    attachments,
                    messages);
    private final User client = User.builder().id(UUID.randomUUID()).fullName("Client").build();
    private final User freelancer =
            User.builder().id(UUID.randomUUID()).fullName("Freelancer").build();
    private final ChatRoom room =
            ChatRoom.builder()
                    .id(UUID.randomUUID())
                    .client(client)
                    .freelancer(freelancer)
                    .job(Job.builder().build())
                    .build();

    private void setupRoom() {
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
    }

    @Test
    void outsidersCannotQueryEitherResourceHistory() {
        setupRoom();
        var outsider = UUID.randomUUID();
        assertThrows(
                ApiException.class,
                () -> service.attachments(room.getId(), outsider, "images", 0, 18));
        assertThrows(ApiException.class, () -> service.links(room.getId(), outsider, 0, 18));
        verifyNoInteractions(attachments, messages);
    }

    @Test
    void bothMembersCanReadOlderAttachmentsWithStablePagination() {
        setupRoom();
        var sentAt = LocalDateTime.of(2026, 10, 1, 10, 0);
        var message =
                ChatMessage.builder()
                        .id(UUID.randomUUID())
                        .room(room)
                        .sender(freelancer)
                        .createdAt(sentAt)
                        .build();
        var attachment =
                ChatAttachment.builder()
                        .id(UUID.randomUUID())
                        .room(room)
                        .message(message)
                        .fileName("brief.pdf")
                        .contentType("application/pdf")
                        .sizeBytes(128)
                        .createdAt(sentAt.minusHours(1))
                        .build();
        when(attachments.findShared(eq(room.getId()), anyBoolean(), any(Pageable.class)))
                .thenAnswer(
                        invocation ->
                                new PageImpl<>(
                                        List.of(attachment),
                                        invocation.getArgument(2, Pageable.class),
                                        60));
        for (var member : List.of(client, freelancer)) {
            var result = service.attachments(room.getId(), member.getId(), "files", 2, 18);
            assertEquals(2, result.page());
            assertEquals(18, result.size());
            assertEquals(60, result.totalElements());
            assertTrue(result.hasNext());
            assertEquals(message.getId(), result.items().getFirst().messageId());
            assertEquals("Freelancer", result.items().getFirst().senderName());
            assertEquals(sentAt, result.items().getFirst().sentAt());
        }
        var request = ArgumentCaptor.forClass(Pageable.class);
        verify(attachments, times(2)).findShared(eq(room.getId()), eq(false), request.capture());
        assertEquals(
                Sort.Direction.DESC,
                request.getValue().getSort().getOrderFor("message.createdAt").getDirection());
        assertEquals(
                Sort.Direction.DESC, request.getValue().getSort().getOrderFor("id").getDirection());
        service.attachments(room.getId(), client.getId(), "images", 0, 18);
        verify(attachments).findShared(eq(room.getId()), eq(true), any(Pageable.class));
    }

    @Test
    void badCategoryOrPageIsRejectedBeforeQuerying() {
        setupRoom();
        assertThrows(
                ApiException.class,
                () -> service.attachments(room.getId(), client.getId(), "all", 0, 18));
        assertThrows(
                ApiException.class,
                () -> service.attachments(room.getId(), client.getId(), null, 0, 18));
        assertThrows(
                ApiException.class,
                () -> service.attachments(room.getId(), client.getId(), "files", -1, 18));
        assertThrows(ApiException.class, () -> service.links(room.getId(), client.getId(), 0, 101));
        verifyNoInteractions(attachments, messages);
    }

    @Test
    void sharedLinksRetainSenderDateAndPagination() {
        setupRoom();
        var sentAt = LocalDateTime.of(2026, 10, 1, 10, 0);
        var message =
                ChatMessage.builder()
                        .id(UUID.randomUUID())
                        .sender(client)
                        .createdAt(sentAt)
                        .content("Tài liệu: https://example.com/brief.")
                        .build();
        when(messages.findSharedLinks(eq(room.getId()), any(Pageable.class)))
                .thenAnswer(
                        invocation ->
                                new PageImpl<>(
                                        List.of(message),
                                        invocation.getArgument(1, Pageable.class),
                                        40));
        var result = service.links(room.getId(), freelancer.getId(), 1, 18);
        assertEquals(1, result.page());
        assertTrue(result.hasNext());
        assertEquals(40, result.totalElements());
        assertEquals(message.getId(), result.items().getFirst().messageId());
        assertEquals("Client", result.items().getFirst().senderName());
        assertEquals(sentAt, result.items().getFirst().sentAt());
        assertEquals(List.of("https://example.com/brief"), result.items().getFirst().links());
    }

    @Test
    void extractsUniqueWebLinksWithoutSentencePunctuation() {
        assertEquals(
                List.of(
                        "https://example.com/brief",
                        "https://www.example.com/docs",
                        "http://example.com/a_(b)"),
                ChatResourceService.extractLinks(
                        "Xem (https://example.com/brief). www.example.com/docs! "
                                + "https://example.com/brief và [http://example.com/a_(b)]."));
        assertEquals(List.of(), ChatResourceService.extractLinks(null));
        assertEquals(List.of(), ChatResourceService.extractLinks("Không có link"));
        assertEquals(
                List.of(),
                ChatResourceService.extractLinks(
                        "javascript:alert(1) file:///tmp/x https:// https://user:pass@example.com https://example.com:99999 https://example.com/%ZZ"));
    }
}
