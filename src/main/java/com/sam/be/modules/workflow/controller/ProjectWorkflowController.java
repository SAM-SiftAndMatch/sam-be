package com.sam.be.modules.workflow.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.workflow.dto.request.DeliveryCreateRequest;
import com.sam.be.modules.workflow.dto.request.DeliveryRevisionRequest;
import com.sam.be.modules.workflow.dto.request.TaskReportRequest;
import com.sam.be.modules.workflow.dto.response.DeliveryAcceptanceResponse;
import com.sam.be.modules.workflow.dto.response.ProjectDeliveryResponse;
import com.sam.be.modules.workflow.dto.response.ProjectTimelineResponse;
import com.sam.be.modules.workflow.dto.response.QaPlanResponse;
import com.sam.be.modules.workflow.dto.response.TaskReportResponse;
import com.sam.be.modules.workflow.service.ProjectWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Project Workflow", description = "Timeline, task reports and product deliveries")
public class ProjectWorkflowController {

    private final ProjectWorkflowService workflowService;

    @GetMapping("/{jobId}/timeline/summary")
    @Operation(summary = "Get project timeline without report history")
    public ApiResponse<ProjectTimelineResponse> getTimelineSummary(@PathVariable UUID jobId) {
        return ApiResponse.<ProjectTimelineResponse>builder()
                .result(workflowService.getTimelineSummary(SecurityUtils.getCurrentUserId(), jobId))
                .build();
    }

    @GetMapping("/{jobId}/timeline/tasks/{taskId}/reports")
    @Operation(summary = "Get all reports for one timeline task")
    public ApiResponse<List<TaskReportResponse>> getTaskReports(
            @PathVariable UUID jobId, @PathVariable UUID taskId) {
        return ApiResponse.<List<TaskReportResponse>>builder()
                .result(
                        workflowService.getTaskReports(
                                SecurityUtils.getCurrentUserId(), jobId, taskId))
                .build();
    }

    @PostMapping("/{jobId}/timeline/tasks/{taskId}/reports")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Freelancer reports progress for a timeline task")
    public ApiResponse<ProjectTimelineResponse> addTaskReport(
            @PathVariable UUID jobId,
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskReportRequest request) {
        return ApiResponse.<ProjectTimelineResponse>builder()
                .result(
                        workflowService.addTaskReport(
                                SecurityUtils.getCurrentUserId(), jobId, taskId, request))
                .build();
    }

    @GetMapping("/{jobId}/deliveries")
    @Operation(summary = "List product delivery versions")
    public ApiResponse<List<ProjectDeliveryResponse>> getDeliveries(@PathVariable UUID jobId) {
        return ApiResponse.<List<ProjectDeliveryResponse>>builder()
                .result(workflowService.getDeliveries(SecurityUtils.getCurrentUserId(), jobId))
                .build();
    }

    @PostMapping("/{jobId}/deliveries")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(summary = "Freelancer submits a website delivery")
    public ApiResponse<ProjectDeliveryResponse> submitDelivery(
            @PathVariable UUID jobId, @Valid @RequestBody DeliveryCreateRequest request) {
        return ApiResponse.<ProjectDeliveryResponse>builder()
                .result(
                        workflowService.submitDelivery(
                                SecurityUtils.getCurrentUserId(), jobId, request))
                .build();
    }

    @GetMapping("/{jobId}/deliveries/{deliveryId}/qa-plan")
    @Operation(summary = "Get the AI-generated manual acceptance checklist")
    public ApiResponse<QaPlanResponse> getQaPlan(
            @PathVariable UUID jobId, @PathVariable UUID deliveryId) {
        return ApiResponse.<QaPlanResponse>builder()
                .result(
                        workflowService.getQaPlan(
                                SecurityUtils.getCurrentUserId(), jobId, deliveryId))
                .build();
    }

    @PostMapping("/{jobId}/deliveries/{deliveryId}/request-revision")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "Client requests changes for a delivery")
    public ApiResponse<ProjectDeliveryResponse> requestDeliveryRevision(
            @PathVariable UUID jobId,
            @PathVariable UUID deliveryId,
            @Valid @RequestBody DeliveryRevisionRequest request) {
        return ApiResponse.<ProjectDeliveryResponse>builder()
                .result(
                        workflowService.requestDeliveryRevision(
                                SecurityUtils.getCurrentUserId(), jobId, deliveryId, request))
                .build();
    }

    @PostMapping("/{jobId}/deliveries/{deliveryId}/accept")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Client manually accepts a delivery and triggers sandbox settlement",
            description = "This credits the sandbox wallet only; no real payout is sent")
    public ApiResponse<DeliveryAcceptanceResponse> acceptDelivery(
            @PathVariable UUID jobId, @PathVariable UUID deliveryId) {
        return ApiResponse.<DeliveryAcceptanceResponse>builder()
                .result(
                        workflowService.acceptDelivery(
                                SecurityUtils.getCurrentUserId(), jobId, deliveryId))
                .build();
    }
}
