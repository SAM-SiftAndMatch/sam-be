package com.sam.be.modules.workflow.service;

import com.sam.be.common.response.PageResponse;
import com.sam.be.modules.workflow.dto.request.DeliveryCreateRequest;
import com.sam.be.modules.workflow.dto.request.DeliveryRevisionRequest;
import com.sam.be.modules.workflow.dto.request.TaskReportRequest;
import com.sam.be.modules.workflow.dto.response.DeliveryAcceptanceResponse;
import com.sam.be.modules.workflow.dto.response.ProjectDeliveryResponse;
import com.sam.be.modules.workflow.dto.response.ProjectTimelineResponse;
import com.sam.be.modules.workflow.dto.response.QaPlanResponse;
import com.sam.be.modules.workflow.dto.response.TaskReportResponse;
import java.util.List;
import java.util.UUID;

public interface ProjectWorkflowService {
    ProjectTimelineResponse getTimeline(UUID userId, UUID jobId);

    ProjectTimelineResponse getTimelineSummary(UUID userId, UUID jobId);

    PageResponse<TaskReportResponse> getTaskReportsPage(
            UUID userId, UUID jobId, UUID taskId, int page, int size);

    ProjectTimelineResponse addTaskReport(
            UUID freelancerId, UUID jobId, UUID taskId, TaskReportRequest request);

    List<ProjectDeliveryResponse> getDeliveries(UUID userId, UUID jobId);

    PageResponse<ProjectDeliveryResponse> getDeliveriesPage(
            UUID userId, UUID jobId, int page, int size);

    ProjectDeliveryResponse submitDelivery(
            UUID freelancerId, UUID jobId, DeliveryCreateRequest request);

    QaPlanResponse getQaPlan(UUID userId, UUID jobId, UUID deliveryId);

    ProjectDeliveryResponse requestDeliveryRevision(
            UUID clientId, UUID jobId, UUID deliveryId, DeliveryRevisionRequest request);

    DeliveryAcceptanceResponse acceptDelivery(UUID clientId, UUID jobId, UUID deliveryId);
}
