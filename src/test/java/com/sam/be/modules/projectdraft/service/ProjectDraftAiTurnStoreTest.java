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
    private final ProjectDraftAiTurnStore store =
            new ProjectDraftAiTurnStore(draftService, drafts, turns, new ObjectMapper());

    @Test
    void rejectedPromptPreservesSrsBudgetPhaseAndProgress() {
        UUID clientId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        ProjectDraft draft = new ProjectDraft();
        draft.setId(draftId);
        draft.setFlowType("AI");
        draft.setSrsContent("Original SRS");
        draft.setSrsDocumentUrl("https://example.test/original");
        draft.setExactBudgetVnd(new java.math.BigDecimal("3000000"));
        draft.setDurationMonths(java.math.BigDecimal.ONE);
        draft.setProgressStep(4);
        draft.setPhase("completed");
        when(drafts.findOwnedForUpdate(draftId, clientId)).thenReturn(Optional.of(draft));
        when(drafts.findByIdForUpdate(draftId)).thenReturn(Optional.of(draft));
        when(turns.saveAndFlush(any(ProjectDraftAiTurn.class))).thenAnswer(i -> i.getArgument(0));
        ProjectDraftAiTurn turn =
                store.submit(
                        clientId,
                        draftId,
                        new CreateDraftAiTurnRequest(
                                UUID.randomUUID(), "BA", "Kể chuyện không liên quan"));
        turn.setId(UUID.randomUUID());
        turn.setStatus("PROCESSING");
        when(turns.findById(turn.getId())).thenReturn(Optional.of(turn));
        store.complete(turn.getId(), com.sam.be.modules.ai.service.ProjectPromptGuard.refused());
        assertEquals("Original SRS", draft.getSrsContent());
        assertEquals("https://example.test/original", draft.getSrsDocumentUrl());
        assertEquals(new java.math.BigDecimal("3000000"), draft.getExactBudgetVnd());
        assertEquals(4, draft.getProgressStep());
        assertEquals("completed", draft.getPhase());
        org.mockito.Mockito.verify(turns, org.mockito.Mockito.never()).deactivateRiskTurns(any());
    }

    @Test
    void aiMilestonesFollowCompletedResponsesAndRevisions() {
        UUID clientId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        ProjectDraft draft = new ProjectDraft();
        draft.setId(draftId);
        draft.setFlowType("AI");
        when(drafts.findOwnedForUpdate(draftId, clientId)).thenReturn(Optional.of(draft));
        when(drafts.findByIdForUpdate(draftId)).thenReturn(Optional.of(draft));
        when(turns.saveAndFlush(any(ProjectDraftAiTurn.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectDraftAiTurn ba =
                store.submit(
                        clientId,
                        draftId,
                        new CreateDraftAiTurnRequest(
                                UUID.randomUUID(), "BA", "Tôi muốn làm website"));
        assertEquals(0, draft.getProgressStep());
        ba.setId(UUID.randomUUID());
        ba.setStatus("PROCESSING");
        when(turns.findById(ba.getId())).thenReturn(Optional.of(ba));
        store.complete(
                ba.getId(),
                AiChatResponse.builder().status("COMPLETED").srsContent("SRS hoàn chỉnh").build());
        assertEquals(2, draft.getProgressStep());

        ProjectDraftAiTurn risk =
                store.submit(
                        clientId,
                        draftId,
                        new CreateDraftAiTurnRequest(UUID.randomUUID(), "RISK", "Đánh giá rủi ro"));
        assertEquals(2, draft.getProgressStep());
        risk.setId(UUID.randomUUID());
        risk.setStatus("PROCESSING");
        when(turns.findById(risk.getId())).thenReturn(Optional.of(risk));
        store.complete(risk.getId(), AiChatResponse.builder().status("FINALIZED").build());
        assertEquals(4, draft.getProgressStep());

        ProjectDraftAiTurn revision =
                store.submit(
                        clientId,
                        draftId,
                        new CreateDraftAiTurnRequest(UUID.randomUUID(), "BA", "Cần sửa SRS"));
        assertEquals(4, draft.getProgressStep());
        revision.setId(UUID.randomUUID());
        revision.setStatus("PROCESSING");
        when(turns.findById(revision.getId())).thenReturn(Optional.of(revision));
        store.complete(revision.getId(), AiChatResponse.builder().status("ASKING").build());
        assertEquals(1, draft.getProgressStep());
    }
}
