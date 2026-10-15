package com.sam.be.modules.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sam.be.common.constant.enums.ContractStatus;
import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.modules.chat.entity.ChatMessage;
import com.sam.be.modules.chat.entity.ChatRoom;
import com.sam.be.modules.chat.repository.ChatMessageRepository;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import com.sam.be.modules.chat.service.impl.ChatServiceImpl;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatServiceImplTest {

    @Test
    void cursorPageReturnsOldestToNewestWithNextCursor() {
        ChatRoomRepository rooms = mock(ChatRoomRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        UUID userId = UUID.randomUUID();
        ChatRoom room =
                ChatRoom.builder()
                        .id(UUID.randomUUID())
                        .client(User.builder().id(userId).build())
                        .freelancer(User.builder().id(UUID.randomUUID()).build())
                        .build();
        LocalDateTime now = LocalDateTime.now();
        ChatMessage newest =
                ChatMessage.builder()
                        .id(UUID.randomUUID())
                        .room(room)
                        .content("newest")
                        .createdAt(now)
                        .build();
        ChatMessage older =
                ChatMessage.builder()
                        .id(UUID.randomUUID())
                        .room(room)
                        .content("older")
                        .createdAt(now.minusMinutes(1))
                        .build();
        ChatMessage extra =
                ChatMessage.builder()
                        .id(UUID.randomUUID())
                        .room(room)
                        .content("extra")
                        .createdAt(now.minusMinutes(2))
                        .build();
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(messages.findRecentByRoomId(eq(room.getId()), any()))
                .thenReturn(List.of(newest, older, extra));
        var service =
                new ChatServiceImpl(
                        rooms,
                        messages,
                        mock(UserRepository.class),
                        mock(ContractRepository.class));

        var result = service.getMessagePage(room.getId(), userId, null, 2);

        assertEquals("older", result.items().getFirst().getContent());
        assertEquals("newest", result.items().getLast().getContent());
        assertEquals(older.getCreatedAt() + "|" + older.getId(), result.nextCursor());
        assertEquals(true, result.hasMore());
        verify(messages).findRecentByRoomId(eq(room.getId()), any());
        verifyNoMoreInteractions(messages);
    }

    @Test
    void cursorPageUsesNonNullableBeforeQuery() {
        ChatRoomRepository rooms = mock(ChatRoomRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        UUID userId = UUID.randomUUID();
        ChatRoom room =
                ChatRoom.builder()
                        .id(UUID.randomUUID())
                        .client(User.builder().id(userId).build())
                        .freelancer(User.builder().id(UUID.randomUUID()).build())
                        .build();
        LocalDateTime beforeAt = LocalDateTime.now();
        UUID beforeId = UUID.randomUUID();
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        when(messages.findHistoryBefore(eq(room.getId()), eq(beforeAt), eq(beforeId), any()))
                .thenReturn(List.of());
        var service =
                new ChatServiceImpl(
                        rooms,
                        messages,
                        mock(UserRepository.class),
                        mock(ContractRepository.class));

        var result = service.getMessagePage(room.getId(), userId, beforeAt + "|" + beforeId, 10);

        assertEquals(0, result.items().size());
        verify(messages).findHistoryBefore(eq(room.getId()), eq(beforeAt), eq(beforeId), any());
        verifyNoMoreInteractions(messages);
    }

    @Test
    void recentMessagesStillRequireRoomMembership() {
        ChatRoomRepository rooms = mock(ChatRoomRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ChatRoom room =
                ChatRoom.builder()
                        .id(UUID.randomUUID())
                        .client(User.builder().id(UUID.randomUUID()).build())
                        .freelancer(User.builder().id(UUID.randomUUID()).build())
                        .build();
        when(rooms.findById(room.getId())).thenReturn(Optional.of(room));
        var service =
                new ChatServiceImpl(
                        rooms,
                        messages,
                        mock(UserRepository.class),
                        mock(ContractRepository.class));

        assertThrows(
                ApiException.class,
                () -> service.getRecentMessages(room.getId(), UUID.randomUUID(), 50));
        verifyNoMoreInteractions(messages);
    }

    @Test
    void roomListLoadsLastMessagesAndStatusesInBatches() {
        ChatRoomRepository rooms = mock(ChatRoomRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ContractRepository contracts = mock(ContractRepository.class);
        UUID userId = UUID.randomUUID();
        User client = User.builder().id(userId).fullName("Client").build();
        User freelancer = User.builder().id(UUID.randomUUID()).fullName("Freelancer").build();
        Job job = Job.builder().id(UUID.randomUUID()).title("Job").build();
        ChatRoom room =
                ChatRoom.builder()
                        .id(UUID.randomUUID())
                        .job(job)
                        .client(client)
                        .freelancer(freelancer)
                        .build();
        ChatMessage last =
                ChatMessage.builder()
                        .id(UUID.randomUUID())
                        .room(room)
                        .content("Last message")
                        .createdAt(LocalDateTime.now())
                        .build();
        Contract contract = Contract.builder().job(job).freelancer(freelancer).status(ContractStatus.ACTIVE).build();
        when(rooms.findAllByMemberId(userId)).thenReturn(List.of(room));
        when(messages.findLatestForRooms(List.of(room.getId()))).thenReturn(List.of(last));
        when(contracts.findAllByJobIdIn(List.of(job.getId()))).thenReturn(List.of(contract));

        var service = new ChatServiceImpl(rooms, messages, mock(UserRepository.class), contracts);
        var result = service.getMyRooms(userId);

        assertEquals("Last message", result.getFirst().getLastMessage());
        assertEquals(ContractStatus.ACTIVE, result.getFirst().getContractStatus());
        verify(messages).findLatestForRooms(List.of(room.getId()));
        verify(contracts).findAllByJobIdIn(List.of(job.getId()));
        verifyNoMoreInteractions(messages, contracts);
    }

    @Test
    void contractStatusIsHiddenInOtherFreelancerRoom() {
        ChatRoomRepository rooms = mock(ChatRoomRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        ContractRepository contracts = mock(ContractRepository.class);
        User client = User.builder().id(UUID.randomUUID()).fullName("Client").build();
        User selected = User.builder().id(UUID.randomUUID()).build();
        User other = User.builder().id(UUID.randomUUID()).fullName("Other").build();
        Job job = Job.builder().id(UUID.randomUUID()).title("Job").status(JobStatus.OPEN).build();
        ChatRoom otherRoom = ChatRoom.builder().id(UUID.randomUUID()).job(job).client(client).freelancer(other).build();
        Contract contract = Contract.builder().job(job).freelancer(selected).status(ContractStatus.ACTIVE).build();
        when(rooms.findAllByMemberId(client.getId())).thenReturn(List.of(otherRoom));
        when(messages.findLatestForRooms(List.of(otherRoom.getId()))).thenReturn(List.of());
        when(contracts.findAllByJobIdIn(List.of(job.getId()))).thenReturn(List.of(contract));

        var service = new ChatServiceImpl(rooms, messages, mock(UserRepository.class), contracts);
        var result = service.getMyRooms(client.getId());

        assertNull(result.getFirst().getContractStatus());
    }
}
