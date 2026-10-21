package com.sam.be.modules.projectdraft.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.ai.dto.response.AiChatResponse;
import com.sam.be.modules.projectdraft.dto.CreateDraftAiTurnRequest;
import com.sam.be.modules.projectdraft.entity.ProjectDraftAiTurn;
import com.sam.be.modules.projectdraft.repository.ProjectDraftAiTurnRepository;
import com.sam.be.modules.projectdraft.repository.ProjectDraftRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectDraftAiTurnStore {
    private final ProjectDraftService draftService;
    private final ProjectDraftRepository drafts;
    private final ProjectDraftAiTurnRepository turns;
    private final ObjectMapper mapper;

    @Transactional
    public ProjectDraftAiTurn submit(UUID clientId, UUID draftId, CreateDraftAiTurnRequest request) {
        var draft = drafts.findOwnedForUpdate(draftId, clientId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        draftService.requireEditing(draft);
        if (!"AI".equals(draft.getFlowType())) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Bản nháp này không dùng AI Brief");
        }
        var existing = turns.findByDraftIdAndClientMessageId(draftId, request.clientMessageId());
        if (existing.isPresent()) {
            ProjectDraftAiTurn turn = existing.get();
            if (!turn.getPhase().equals(request.phase()) || !turn.getUserMessage().equals(request.userMessage())) {
                throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Mã tin nhắn đã được sử dụng");
            }
            if ("FAILED".equals(turn.getStatus())) {
                turn.setStatus("PENDING");
                turn.setAttempts(0);
            }
            return turn;
        }
        if (turns.existsByDraftIdAndStatusIn(draftId, List.of("PENDING", "PROCESSING"))) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Vui lòng chờ AI trả lời tin trước");
        }
        if ("RISK".equals(request.phase()) && (draft.getSrsContent() == null || draft.getSrsContent().isBlank())) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Chưa có SRS để đánh giá rủi ro");
        }
        ProjectDraftAiTurn turn = new ProjectDraftAiTurn();
        turn.setDraft(draft);
        turn.setClientMessageId(request.clientMessageId());
        turn.setPhase(request.phase());
        turn.setUserMessage(request.userMessage().trim());
        if ("BA".equals(request.phase())) {
            turns.deactivateRiskTurns(draftId);
            draft.setSrsContent(null);
            draft.setSrsDocumentUrl(null);
            draft.setExactBudgetVnd(null);
            draft.setDurationMonths(null);
            draft.setProgressStep(1);
        } else {
            draft.setProgressStep(3);
        }
        draft.setPhase("BA".equals(request.phase()) ? "ba-chat" : "risk-chat");
        return turns.saveAndFlush(turn);
    }

    @Transactional
    public boolean claim(UUID turnId) {
        return turns.claim(turnId) == 1;
    }

    @Transactional
    public void complete(UUID turnId, AiChatResponse response) {
        ProjectDraftAiTurn turn = turns.findById(turnId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!"PROCESSING".equals(turn.getStatus())) return;
        try { turn.setAiResponseJson(mapper.writeValueAsString(response)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot save AI answer", e); }
        turn.setStatus("COMPLETED");
        var draft = drafts.findByIdForUpdate(turn.getDraft().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (response.getSrsContent() != null && !response.getSrsContent().isBlank()) {
            draft.setSrsContent(response.getSrsContent());
        }
        if (response.getCurrentSrsUrl() != null) draft.setSrsDocumentUrl(response.getCurrentSrsUrl());
        if (response.getExactBudgetVnd() != null) draft.setExactBudgetVnd(response.getExactBudgetVnd());
        if (response.getDurationMonths() != null) draft.setDurationMonths(response.getDurationMonths());
        if ("BA".equals(turn.getPhase()) && "COMPLETED".equals(response.getStatus())
                && draft.getSrsContent() != null && !draft.getSrsContent().isBlank()) {
            draft.setPhase("srs-review");
            draft.setProgressStep(2);
        } else if ("RISK".equals(turn.getPhase()) && "FINALIZED".equals(response.getStatus())
                && draft.getSrsContent() != null && !draft.getSrsContent().isBlank()) {
            draft.setPhase("completed");
            draft.setProgressStep(4);
        }
    }

    @Transactional
    public void fail(UUID turnId) {
        turns.findById(turnId).ifPresent(turn -> {
            if ("PROCESSING".equals(turn.getStatus())) turn.setStatus("FAILED");
        });
    }

    @Transactional
    public void recoverStale() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        turns.recoverStale(threshold);
        turns.failStale(threshold);
    }
}
