package com.sam.be.modules.projectdraft.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.modules.ai.dto.response.AiChatResponse;
import com.sam.be.modules.projectdraft.dto.CreateDraftAiTurnRequest;
import com.sam.be.modules.projectdraft.entity.ProjectDraft;
import com.sam.be.modules.projectdraft.entity.ProjectDraftAiTurn;
import com.sam.be.modules.projectdraft.repository.ProjectDraftAiTurnRepository;
import com.sam.be.modules.projectdraft.repository.ProjectDraftRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectDraftAiTurnStoreTest {
    private final ProjectDraftService draftService = mock(ProjectDraftService.class);
    private final ProjectDraftRepository drafts = mock(ProjectDraftRepository.class);
    private final ProjectDraftAiTurnRepository turns = mock(ProjectDraftAiTurnRepository.class);
    private final ProjectDraftAiTurnStore store = new ProjectDraftAiTurnStore(
            draftService, drafts, turns, new ObjectMapper());

    @Test
    void aiMilestonesFollowCompletedResponsesAndRevisions() {
        UUID clientId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        ProjectDraft draft = new ProjectDraft();
        draft.setId(draftId);
        draft.setFlowType("AI");
        when(drafts.findOwnedForUpdate(draftId, clientId)).thenReturn(Optional.of(draft));
        when(drafts.findByIdForUpdate(draftId)).thenReturn(Optional.of(draft));
        when(turns.saveAndFlush(any(ProjectDraftAiTurn.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectDraftAiTurn ba = store.submit(clientId, draftId,
                new CreateDraftAiTurnRequest(UUID.randomUUID(), "BA", "Tôi muốn làm website"));
        assertEquals(1, draft.getProgressStep());
        ba.setId(UUID.randomUUID());
        ba.setStatus("PROCESSING");
        when(turns.findById(ba.getId())).thenReturn(Optional.of(ba));
        store.complete(ba.getId(), AiChatResponse.builder()
                .status("COMPLETED").srsContent("SRS hoàn chỉnh").build());
        assertEquals(2, draft.getProgressStep());

        ProjectDraftAiTurn risk = store.submit(clientId, draftId,
                new CreateDraftAiTurnRequest(UUID.randomUUID(), "RISK", "Đánh giá rủi ro"));
        assertEquals(3, draft.getProgressStep());
        risk.setId(UUID.randomUUID());
        risk.setStatus("PROCESSING");
        when(turns.findById(risk.getId())).thenReturn(Optional.of(risk));
        store.complete(risk.getId(), AiChatResponse.builder().status("FINALIZED").build());
        assertEquals(4, draft.getProgressStep());

        store.submit(clientId, draftId,
                new CreateDraftAiTurnRequest(UUID.randomUUID(), "BA", "Cần sửa SRS"));
        assertEquals(1, draft.getProgressStep());
    }
}
