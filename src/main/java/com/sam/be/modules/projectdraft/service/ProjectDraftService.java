package com.sam.be.modules.projectdraft.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.PageRequests;
import com.sam.be.common.response.PageResponse;
import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.JobResponse;
import com.sam.be.modules.job.service.JobPublishingService;
import com.sam.be.modules.job.service.JobService;
import com.sam.be.modules.projectdraft.dto.*;
import com.sam.be.modules.projectdraft.entity.ProjectDraft;
import com.sam.be.modules.projectdraft.entity.ProjectDraftAiTurn;
import com.sam.be.modules.projectdraft.repository.ProjectDraftAiTurnRepository;
import com.sam.be.modules.projectdraft.repository.ProjectDraftRepository;
import com.sam.be.modules.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectDraftService {
    private final ProjectDraftRepository drafts;
    private final ProjectDraftAiTurnRepository turns;
    private final UserRepository users;
    private final JobPublishingService publishing;
    private final JobService jobs;
    private final ObjectMapper mapper;

    @Transactional
    public ProjectDraftResponse create(UUID clientId, CreateProjectDraftRequest request) {
        ProjectDraft draft = new ProjectDraft();
        draft.setClient(users.getReferenceById(clientId));
        draft.setFlowType(request.flowType());
        draft.setPhase("AI".equals(request.flowType()) ? "brief" : "info");
        draft.setTitle(request.title());
        draft.setBrief(request.brief());
        drafts.saveAndFlush(draft);
        return response(draft, List.of());
    }

    @Transactional(readOnly = true)
    public ProjectDraftResponse get(UUID clientId, UUID draftId) {
        ProjectDraft draft = owned(clientId, draftId);
        return response(draft, turns.findByDraftIdAndActiveTrueOrderByCreatedAtAscIdAsc(draftId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectDraftSummaryResponse> page(UUID clientId, int page, int size) {
        var result = drafts.findByClientIdAndStatus(clientId, "EDITING",
                PageRequests.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return PageResponse.from(result.map(d -> new ProjectDraftSummaryResponse(
                d.getId(), d.getFlowType(), d.getPhase(), d.getProgressStep(), d.getProgressStep() * 20,
                d.getTitle(), d.getBrief(), d.getUpdatedAt())));
    }

    @Transactional
    public ProjectDraftSaveResponse update(UUID clientId, UUID draftId, UpdateProjectDraftRequest request) {
        ProjectDraft draft = drafts.findOwnedForUpdate(draftId, clientId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        requireEditing(draft);
        if (draft.getEditRevision() != request.expectedRevision()) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE,
                    "Bản nháp đã thay đổi ở nơi khác. Vui lòng tải lại trước khi tiếp tục.");
        }
        String previousPhase = draft.getPhase();
        String previousTitle = draft.getTitle();
        String previousBrief = draft.getBrief();
        Map<String, Object> previousState = "MANUAL".equals(draft.getFlowType())
                ? parseState(draft.getStateJson()) : Map.of();
        if (request.title() != null) draft.setTitle(request.title());
        if (request.brief() != null) draft.setBrief(request.brief());
        if (request.phase() != null) draft.setPhase(request.phase());
        if (request.state() != null) draft.setStateJson(json(request.state()));
        if (request.srsDocumentUrl() != null) draft.setSrsDocumentUrl(request.srsDocumentUrl());
        if ("MANUAL".equals(draft.getFlowType())) {
            updateManualProgress(draft, previousPhase, previousTitle, previousBrief, previousState);
        } else if ("brief".equals(draft.getPhase()) && draft.getProgressStep() > 0
                && !Objects.equals(previousBrief, draft.getBrief())) {
            if (turns.existsByDraftIdAndStatusIn(draftId, List.of("PENDING", "PROCESSING"))) {
                throw new ApiException(ErrorCode.REQUEST_FAILED, "Vui lòng chờ AI trả lời trước khi sửa mô tả");
            }
            turns.deactivateAllTurns(draftId);
            draft.setSrsContent(null);
            draft.setSrsDocumentUrl(null);
            draft.setExactBudgetVnd(null);
            draft.setDurationMonths(null);
            draft.setProgressStep(0);
        }
        draft.setEditRevision(draft.getEditRevision() + 1);
        drafts.saveAndFlush(draft);
        return new ProjectDraftSaveResponse(draft.getEditRevision());
    }

    @Transactional
    public void delete(UUID clientId, UUID draftId) {
        ProjectDraft draft = owned(clientId, draftId);
        requireEditing(draft);
        draft.setStatus("DELETED");
    }

    @Transactional
    public JobResponse publish(UUID clientId, UUID draftId, JobCreateRequest request) {
        ProjectDraft draft = drafts.findOwnedForUpdate(draftId, clientId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (draft.getPublishedJobId() != null) {
            return jobs.getJobById(draft.getPublishedJobId());
        }
        requireEditing(draft);
        if (turns.existsByDraftIdAndStatusIn(draftId, List.of("PENDING", "PROCESSING"))) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Vui lòng chờ AI trả lời trước khi đăng dự án");
        }
        if (!"confirm".equals(draft.getPhase())) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Bản nháp chưa đến bước xác nhận đăng dự án");
        }
        if ("AI".equals(draft.getFlowType())) {
            if (draft.getSrsContent() == null || draft.getSrsContent().isBlank()
                    || draft.getSrsDocumentUrl() == null || draft.getSrsDocumentUrl().isBlank()) {
                throw new ApiException(ErrorCode.REQUEST_FAILED, "Chưa hoàn tất và lưu SRS cho dự án");
            }
            request.setSrsContent(draft.getSrsContent());
            request.setSrsDocumentUrl(draft.getSrsDocumentUrl());
        }
        request.setClientRequestId(draftId);
        JobResponse job = publishing.create(clientId, request);
        draft.setPublishedJobId(job.getId());
        draft.setStatus("PUBLISHED");
        draft.setProgressStep(5);
        drafts.saveAndFlush(draft);
        return job;
    }

    public ProjectDraft owned(UUID clientId, UUID draftId) {
        ProjectDraft draft = drafts.findByIdAndClientId(draftId, clientId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if ("DELETED".equals(draft.getStatus())) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        return draft;
    }

    public void requireEditing(ProjectDraft draft) {
        if (!"EDITING".equals(draft.getStatus())) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION, "Bản nháp này không còn được chỉnh sửa");
        }
    }

    public ProjectDraftAiTurnResponse turnResponse(ProjectDraftAiTurn turn) {
        AiChatResponseHolder holder = parseResponse(turn.getAiResponseJson());
        return new ProjectDraftAiTurnResponse(turn.getId(), turn.getClientMessageId(), turn.getPhase(),
                turn.getUserMessage(), turn.getStatus(), holder.response(), turn.getCreatedAt());
    }

    private ProjectDraftResponse response(ProjectDraft draft, List<ProjectDraftAiTurn> history) {
        return new ProjectDraftResponse(draft.getId(), draft.getFlowType(), draft.getPhase(), draft.getStatus(),
                draft.getProgressStep(), draft.getProgressStep() * 20,
                draft.getTitle(), draft.getBrief(), parseState(draft.getStateJson()), draft.getSrsContent(),
                draft.getSrsDocumentUrl(), draft.getExactBudgetVnd(), draft.getDurationMonths(),
                draft.getPublishedJobId(), draft.getVersion(), draft.getEditRevision(), draft.getUpdatedAt(),
                history.stream().map(this::turnResponse).toList());
    }

    private void updateManualProgress(ProjectDraft draft, String previousPhase, String previousTitle,
                                      String previousBrief, Map<String, Object> previousState) {
        Map<String, Object> state = parseState(draft.getStateJson());
        boolean infoValid = hasText(draft.getTitle()) && hasText(draft.getBrief())
                && hasText(state.get("category"));
        boolean budgetValid = state.get("budgetAmount") instanceof Number budget
                && budget.doubleValue() > 0 && hasText(state.get("deadline"));
        int progress = draft.getProgressStep();
        if (!Objects.equals(previousTitle, draft.getTitle())
                || !Objects.equals(previousBrief, draft.getBrief())
                || !Objects.equals(previousState.get("category"), state.get("category"))) {
            progress = Math.min(progress, infoValid ? 1 : 0);
        } else if (!Objects.equals(previousState.get("budgetAmount"), state.get("budgetAmount"))
                || !Objects.equals(previousState.get("deadline"), state.get("deadline"))
                || !Objects.equals(previousState.get("duration"), state.get("duration"))) {
            progress = Math.min(progress, budgetValid ? 2 : 1);
        } else if (!Objects.equals(previousState.get("upgrades"), state.get("upgrades"))) {
            progress = Math.min(progress, 3);
        }
        if (!infoValid) progress = 0;
        else if (!budgetValid) progress = Math.min(progress, 1);
        if (!Objects.equals(previousPhase, draft.getPhase()) && infoValid) {
            int reached = switch (draft.getPhase()) {
                case "budget" -> 1;
                case "addons" -> budgetValid ? 2 : 1;
                case "review" -> budgetValid ? 3 : 1;
                case "confirm" -> budgetValid ? 4 : 1;
                default -> 0;
            };
            progress = Math.max(progress, reached);
        }
        draft.setProgressStep(progress);
    }

    private boolean hasText(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private Map<String, Object> parseState(String json) {
        try { return mapper.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid draft state", e); }
    }

    private String json(Map<String, Object> state) {
        try {
            String value = mapper.writeValueAsString(state);
            if (value.length() > 100_000) throw new ApiException(ErrorCode.VALIDATION_ERROR, "Bản nháp quá lớn");
            return value;
        } catch (JsonProcessingException e) { throw new ApiException(ErrorCode.VALIDATION_ERROR); }
    }

    private AiChatResponseHolder parseResponse(String json) {
        if (json == null) return new AiChatResponseHolder(null);
        try { return new AiChatResponseHolder(mapper.readValue(json, com.sam.be.modules.ai.dto.response.AiChatResponse.class)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid AI response", e); }
    }

    private record AiChatResponseHolder(com.sam.be.modules.ai.dto.response.AiChatResponse response) {}
}
