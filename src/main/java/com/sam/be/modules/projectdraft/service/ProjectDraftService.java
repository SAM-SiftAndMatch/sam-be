package com.sam.be.modules.projectdraft.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                d.getId(), d.getFlowType(), d.getPhase(), d.getTitle(), d.getBrief(), d.getUpdatedAt())));
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
        if (request.title() != null) draft.setTitle(request.title());
        if (request.brief() != null) draft.setBrief(request.brief());
        if (request.phase() != null) draft.setPhase(request.phase());
        if (request.state() != null) draft.setStateJson(json(request.state()));
        if (request.srsDocumentUrl() != null) draft.setSrsDocumentUrl(request.srsDocumentUrl());
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
                draft.getTitle(), draft.getBrief(), parseState(draft.getStateJson()), draft.getSrsContent(),
                draft.getSrsDocumentUrl(), draft.getExactBudgetVnd(), draft.getDurationMonths(),
                draft.getPublishedJobId(), draft.getVersion(), draft.getEditRevision(), draft.getUpdatedAt(),
                history.stream().map(this::turnResponse).toList());
    }

    private JsonNode parseState(String json) {
        try { return mapper.readTree(json); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid draft state", e); }
    }

    private String json(JsonNode state) {
        if (!state.isObject()) throw new ApiException(ErrorCode.VALIDATION_ERROR, "state phải là object");
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
