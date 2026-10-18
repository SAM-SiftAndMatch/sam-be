package com.sam.be.modules.job.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sam.be.modules.job.dto.request.JobCreateRequest;
import com.sam.be.modules.job.dto.response.JobResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class JobPublishingServiceTest {
    private final JobService jobService = mock(JobService.class);
    private final JobAddonService addonService = mock(JobAddonService.class);
    private final JobSrsPdfService pdfService = mock(JobSrsPdfService.class);
    private final JobPublishingService publishing = new JobPublishingService(jobService, addonService, pdfService);

    @BeforeEach
    void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void endTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void queuesPdfOnlyAfterJobCommit() {
        UUID clientId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobCreateRequest request = JobCreateRequest.builder().srsContent("# SRS").build();
        JobResponse job = JobResponse.builder().id(jobId).srsContent("# SRS").build();
        when(jobService.createJob(clientId, request)).thenReturn(job);

        assertEquals(job, publishing.create(clientId, request));
        verify(pdfService, never()).enqueue(any());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(pdfService).enqueue(jobId);
    }

    @Test
    void pdfQueueFailureCannotTurnPublishedJobIntoFailure() {
        UUID clientId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobCreateRequest request = JobCreateRequest.builder().srsContent("# SRS").build();
        JobResponse job = JobResponse.builder().id(jobId).srsContent("# SRS").build();
        when(jobService.createJob(clientId, request)).thenReturn(job);
        doThrow(new IllegalStateException("PDF queue unavailable")).when(pdfService).enqueue(jobId);

        assertEquals(job, publishing.create(clientId, request));
        assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit));
        verify(pdfService).enqueue(jobId);
    }

    @Test
    void failingAddonSelectionDoesNotSchedulePdf() {
        UUID clientId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        JobCreateRequest request = JobCreateRequest.builder().srsContent("# SRS")
                .isFeatured(true).build();
        when(jobService.createJob(clientId, request))
                .thenReturn(JobResponse.builder().id(jobId).srsContent("# SRS").build());
        doThrow(new IllegalStateException("addon failed"))
                .when(addonService).select(clientId, jobId, true, false);

        assertThrows(IllegalStateException.class, () -> publishing.create(clientId, request));
        assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
        verify(pdfService, never()).enqueue(any());
    }
}
