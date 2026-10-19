package com.sam.be.modules.job.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.modules.job.service.JobSrsPdfService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/srs-pdf")
@RequiredArgsConstructor
public class JobSrsPdfController {
    private final JobSrsPdfService service;

    @GetMapping("/status")
    @PreAuthorize("@jobAccessGuard.isOwner(#jobId)")
    public ApiResponse<JobSrsPdfService.Status> status(@PathVariable UUID jobId) {
        return ApiResponse.<JobSrsPdfService.Status>builder().result(service.status(jobId)).build();
    }

    @GetMapping
    @PreAuthorize("@jobAccessGuard.isOwner(#jobId)")
    public ResponseEntity<byte[]> getPdf(@PathVariable UUID jobId,
            @RequestParam(defaultValue = "false") boolean download) {
        byte[] pdf = service.pdf(jobId);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename("srs-" + jobId + ".pdf").build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/retry")
    @PreAuthorize("@jobAccessGuard.isOwner(#jobId)")
    public ApiResponse<JobSrsPdfService.Status> retry(@PathVariable UUID jobId) {
        service.retry(jobId);
        return ApiResponse.<JobSrsPdfService.Status>builder().result(service.status(jobId)).build();
    }
}
