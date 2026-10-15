package com.sam.be.modules.chat.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sam.be.common.exception.ApiException;
import com.sam.be.modules.chat.dto.WorkspaceSummaryDto;
import com.sam.be.modules.chat.repository.ChatRoomRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceSummaryServiceTest {

    @Test
    void returnsOnlyProjectionVisibleToParticipant() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatRoomRepository repository = mock(ChatRoomRepository.class);
        WorkspaceSummaryDto projection =
                new WorkspaceSummaryDto(
                        roomId,
                        UUID.randomUUID(),
                        "Job",
                        null,
                        userId,
                        "Client",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        when(repository.findWorkspaceSummary(roomId, userId)).thenReturn(Optional.of(projection));

        assertSame(projection, new WorkspaceSummaryService(repository).getSummary(roomId, userId));
        verify(repository).findWorkspaceSummary(roomId, userId);
    }

    @Test
    void hidesRoomsThatAreNotVisibleToCaller() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatRoomRepository repository = mock(ChatRoomRepository.class);
        when(repository.findWorkspaceSummary(roomId, userId)).thenReturn(Optional.empty());

        assertThrows(
                ApiException.class,
                () -> new WorkspaceSummaryService(repository).getSummary(roomId, userId));
    }
}
