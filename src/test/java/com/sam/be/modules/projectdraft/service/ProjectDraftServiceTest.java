package com.sam.be.modules.projectdraft.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.exception.ApiException;
import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.JobResponse;
import com.sam.be.modules.job.service.JobPublishingService;
import com.sam.be.modules.job.service.JobService;
import com.sam.be.modules.projectdraft.dto.UpdateProjectDraftRequest;
import com.sam.be.modules.projectdraft.entity.ProjectDraft;
import com.sam.be.modules.projectdraft.repository.ProjectDraftAiTurnRepository;
import com.sam.be.modules.projectdraft.repository.ProjectDraftRepository;
import com.sam.be.modules.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectDraftServiceTest {
    private final ProjectDraftRepository drafts = mock(ProjectDraftRepository.class);
    private final ProjectDraftAiTurnRepository turns = mock(ProjectDraftAiTurnRepository.class);
    private final JobPublishingService publishing = mock(JobPublishingService.class);
    private final JobService jobs = mock(JobService.class);
    private final ProjectDraftService service = new ProjectDraftService(
            drafts, turns, mock(UserRepository.class), publishing, jobs, new ObjectMapper());

    @Test
    void publishUsesStoredAiSrsAndDraftIdAsIdempotencyKey() {
        UUID clientId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ProjectDraft draft = editingDraft(draftId, "AI", "confirm");
        draft.setSrsContent("stored SRS");
        draft.setSrsDocumentUrl("https://example.test/srs.pdf");
        JobCreateRequest request = JobCreateRequest.builder()
                .title("Test project")
                .srsContent("client-supplied SRS")
                .build();
        JobResponse job = JobResponse.builder().id(jobId).build();
        when(drafts.findOwnedForUpdate(draftId, clientId)).thenReturn(Optional.of(draft));
        when(publishing.create(clientId, request)).thenReturn(job);

        assertEquals(job, service.publish(clientId, draftId, request));
        assertEquals(draftId, request.getClientRequestId());
        assertEquals("stored SRS", request.getSrsContent());
        assertEquals("https://example.test/srs.pdf", request.getSrsDocumentUrl());
        assertEquals(jobId, draft.getPublishedJobId());
        assertEquals("PUBLISHED", draft.getStatus());
    }

    @Test
    void repeatedPublishReturnsExistingJobWithoutCreatingAnother() {
        UUID clientId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ProjectDraft draft = editingDraft(draftId, "MANUAL", "confirm");
        draft.setStatus("PUBLISHED");
        draft.setPublishedJobId(jobId);
        JobResponse job = JobResponse.builder().id(jobId).build();
        when(drafts.findOwnedForUpdate(draftId, clientId)).thenReturn(Optional.of(draft));
        when(jobs.getJobById(jobId)).thenReturn(job);

        assertEquals(job, service.publish(clientId, draftId, new JobCreateRequest()));
        verify(publishing, never()).create(any(), any());
    }

    @Test
    void publishRequiresConfirmationPhase() {
        UUID clientId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        when(drafts.findOwnedForUpdate(draftId, clientId))
                .thenReturn(Optional.of(editingDraft(draftId, "MANUAL", "info")));

        assertThrows(ApiException.class,
                () -> service.publish(clientId, draftId, new JobCreateRequest()));
        verify(publishing, never()).create(any(), any());
    }

    @Test
    void staleEditRevisionDoesNotOverwriteDraft() {
        UUID clientId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        ProjectDraft draft = editingDraft(draftId, "MANUAL", "info");
        draft.setEditRevision(3);
        when(drafts.findOwnedForUpdate(draftId, clientId)).thenReturn(Optional.of(draft));

        assertThrows(ApiException.class, () -> service.update(clientId, draftId,
                new UpdateProjectDraftRequest(2L, "stale title", null, null, null, null)));
        assertNull(draft.getTitle());
    }

    private ProjectDraft editingDraft(UUID id, String flow, String phase) {
        ProjectDraft draft = new ProjectDraft();
        draft.setId(id);
        draft.setFlowType(flow);
        draft.setPhase(phase);
        return draft;
    }
}
