package com.sam.be.modules.projectdraft.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.modules.ai.dto.request.AiChatRequest;
import com.sam.be.modules.ai.dto.response.AiChatResponse;
import com.sam.be.modules.ai.service.AiService;
import com.sam.be.modules.projectdraft.dto.ProjectDraftAiTurnResponse;
import com.sam.be.modules.projectdraft.entity.ProjectDraftAiTurn;
import com.sam.be.modules.projectdraft.repository.ProjectDraftAiTurnRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectDraftAiProcessor {
    private final ProjectDraftAiTurnRepository turns;
    private final ProjectDraftAiTurnStore store;
    private final ProjectDraftService drafts;
    private final AiService ai;
    private final ObjectMapper mapper;

    public ProjectDraftAiTurnResponse processNow(UUID turnId) {
        if (store.claim(turnId)) {
            try {
                ProjectDraftAiTurn turn = turns.findWithDraftById(turnId).orElseThrow();
                AiChatRequest request = new AiChatRequest();
                request.setSessionId("draft-" + turn.getDraft().getId());
                request.setUserMessage(turn.getUserMessage());
                AiChatResponse response = "BA".equals(turn.getPhase())
                        ? ai.chatWithAiBaFromHistory(request, baHistory(turn))
                        : riskResponse(turn, request);
                store.complete(turnId, response);
            } catch (Exception ex) {
                log.warn("AI draft turn {} failed: {}", turnId, ex.getMessage());
                store.fail(turnId);
            }
        }
        return drafts.turnResponse(turns.findById(turnId).orElseThrow());
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void recoverPending() {
        store.recoverStale();
        for (UUID turnId : turns.findPendingIds(PageRequest.of(0, 5))) {
            processNow(turnId);
        }
    }

    private List<Map<String, Object>> baHistory(ProjectDraftAiTurn current) {
        List<Map<String, Object>> history = new ArrayList<>();
        for (ProjectDraftAiTurn turn : turns.findByDraftIdAndActiveTrueOrderByCreatedAtAscIdAsc(current.getDraft().getId())) {
            if (!"BA".equals(turn.getPhase()) || !"COMPLETED".equals(turn.getStatus())) continue;
            history.add(geminiMessage("user", turn.getUserMessage()));
            history.add(geminiMessage("model", turn.getAiResponseJson()));
        }
        return history;
    }

    private AiChatResponse riskResponse(ProjectDraftAiTurn current, AiChatRequest request) {
        var draft = current.getDraft();
        request.setCurrentSrsContent(draft.getSrsContent());
        List<Map<String, String>> history = new ArrayList<>();
        for (ProjectDraftAiTurn turn : turns.findByDraftIdAndActiveTrueOrderByCreatedAtAscIdAsc(draft.getId())) {
            if (!"RISK".equals(turn.getPhase()) || !"COMPLETED".equals(turn.getStatus())) continue;
            history.add(Map.of("role", "user", "content", turn.getUserMessage()));
            try {
                AiChatResponse response = mapper.readValue(turn.getAiResponseJson(), AiChatResponse.class);
                history.add(Map.of("role", "assistant", "content", response.getAiMessage() == null ? "" : response.getAiMessage()));
            } catch (Exception ex) {
                throw new IllegalStateException("Stored AI answer is invalid", ex);
            }
        }
        request.setChatHistory(history);
        return ai.chatWithAiRisk(request);
    }

    private Map<String, Object> geminiMessage(String role, String content) {
        return Map.of("role", role, "parts", List.of(Map.of("text", content)));
    }
}
