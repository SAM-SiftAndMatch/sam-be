package com.sam.be.modules.workflow.service.impl;

import com.sam.be.common.constant.enums.DeliveryStatus;
import com.sam.be.common.constant.enums.JobStatus;
import com.sam.be.common.constant.enums.ProjectTaskStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.contract.entity.Contract;
import com.sam.be.modules.contract.repository.ContractRepository;
import com.sam.be.modules.job.entity.Job;
import com.sam.be.modules.job.repository.JobRepository;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import com.sam.be.modules.workflow.dto.request.DeliveryCreateRequest;
import com.sam.be.modules.workflow.dto.request.DeliveryRevisionRequest;
import com.sam.be.modules.workflow.dto.request.TaskReportRequest;
import com.sam.be.modules.workflow.dto.response.DeliveryAcceptanceResponse;
import com.sam.be.modules.workflow.dto.response.ProjectDeliveryResponse;
import com.sam.be.modules.workflow.dto.response.ProjectTimelineResponse;
import com.sam.be.modules.workflow.dto.response.QaPlanResponse;
import com.sam.be.modules.workflow.dto.response.TaskReportResponse;
import com.sam.be.modules.workflow.dto.response.TimelineTaskResponse;
import com.sam.be.modules.workflow.entity.ProjectDelivery;
import com.sam.be.modules.workflow.entity.ProjectTaskReport;
import com.sam.be.modules.workflow.entity.ProjectTimelineTask;
import com.sam.be.modules.workflow.repository.ProjectDeliveryRepository;
import com.sam.be.modules.workflow.repository.ProjectTaskReportRepository;
import com.sam.be.modules.workflow.repository.ProjectTimelineTaskRepository;
import com.sam.be.modules.workflow.service.ManualQaPlanService;
import com.sam.be.modules.workflow.service.ProjectWorkflowService;
import com.sam.be.modules.workflow.service.SrsTimelineParser;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectWorkflowServiceImpl implements ProjectWorkflowService {

    private final JobRepository jobRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ProjectTimelineTaskRepository taskRepository;
    private final ProjectTaskReportRepository reportRepository;
    private final ProjectDeliveryRepository deliveryRepository;
    private final ManualQaPlanService manualQaPlanService;
    private final com.sam.be.modules.wallet.service.SandboxSettlementService
            sandboxSettlementService;

    @Override
    @Transactional
    public ProjectTimelineResponse getTimeline(UUID userId, UUID jobId) {
        Job job = getAccessibleJobForUpdate(userId, jobId);
        List<ProjectTimelineTask> tasks =
                taskRepository.findAllByJobIdOrderByWeekNumberAscSortOrderAsc(jobId);
        if (tasks.isEmpty()) {
            tasks = initializeTimeline(job);
        }
        return toTimelineResponse(job, tasks);
    }

    @Override
    @Transactional
    public ProjectTimelineResponse addTaskReport(
            UUID freelancerId, UUID jobId, UUID taskId, TaskReportRequest request) {
        Job job = getAccessibleJob(freelancerId, jobId);
        Contract contract = requireContract(jobId);
        if (!contract.getFreelancer().getId().equals(freelancerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Chỉ có thể báo cáo khi dự án đang được thực hiện");
        }
        if (request.getStatus() == ProjectTaskStatus.ACCEPTED) {
            throw new ApiException(
                    ErrorCode.FORBIDDEN_ACTION, "Freelancer không thể tự nghiệm thu công việc");
        }

        ProjectTimelineTask task =
                taskRepository
                        .findByIdAndJobId(taskId, jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        User reporter =
                userRepository
                        .findById(freelancerId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        int reportedProgress =
                request.getStatus() == ProjectTaskStatus.SUBMITTED ? 100 : request.getProgress();
        task.setStatus(request.getStatus());
        task.setProgress(reportedProgress);
        if (task.getStartedAt() == null && reportedProgress > 0) task.setStartedAt(now);
        if (reportedProgress == 100) {
            task.setCompletedAt(now);
        } else {
            task.setCompletedAt(null);
        }
        taskRepository.save(task);

        reportRepository.save(
                ProjectTaskReport.builder()
                        .task(task)
                        .reporter(reporter)
                        .content(request.getContent().trim())
                        .evidenceUrl(blankToNull(request.getEvidenceUrl()))
                        .progress(reportedProgress)
                        .reportedStatus(request.getStatus())
                        .build());

        return toTimelineResponse(
                job, taskRepository.findAllByJobIdOrderByWeekNumberAscSortOrderAsc(jobId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectDeliveryResponse> getDeliveries(UUID userId, UUID jobId) {
        getAccessibleJob(userId, jobId);
        return deliveryRepository.findAllByJobIdOrderByVersionNumberDesc(jobId).stream()
                .map(this::toDeliveryResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProjectDeliveryResponse submitDelivery(
            UUID freelancerId, UUID jobId, DeliveryCreateRequest request) {
        Job job = getAccessibleJobForUpdate(freelancerId, jobId);
        Contract contract = requireContract(jobId);
        if (!contract.getFreelancer().getId().equals(freelancerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Chỉ có thể bàn giao khi dự án đang được thực hiện");
        }
        List<ProjectTimelineTask> tasks =
                taskRepository.findAllByJobIdOrderByWeekNumberAscSortOrderAsc(jobId);
        if (tasks.isEmpty()) tasks = initializeTimeline(job);
        if (tasks.stream()
                .anyMatch(
                        task ->
                                task.getProgress() < 100
                                        || (task.getStatus() != ProjectTaskStatus.SUBMITTED
                                                && task.getStatus()
                                                        != ProjectTaskStatus.ACCEPTED))) {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED,
                    "Cần báo cáo hoàn thành toàn bộ timeline trước khi bàn giao sản phẩm");
        }

        ProjectDelivery delivery =
                ProjectDelivery.builder()
                        .job(job)
                        .submittedBy(contract.getFreelancer())
                        .versionNumber((int) deliveryRepository.countByJobId(jobId) + 1)
                        .webUrl(request.getWebUrl().trim())
                        .repositoryUrl(blankToNull(request.getRepositoryUrl()))
                        .note(blankToNull(request.getNote()))
                        .submittedAt(LocalDateTime.now())
                        .build();
        ProjectDelivery savedDelivery = deliveryRepository.save(delivery);
        manualQaPlanService.generateForDelivery(savedDelivery, job.getSrsContent(), tasks);
        return toDeliveryResponse(savedDelivery);
    }

    @Override
    @Transactional(readOnly = true)
    public QaPlanResponse getQaPlan(UUID userId, UUID jobId, UUID deliveryId) {
        getAccessibleJob(userId, jobId);
        deliveryRepository
                .findByIdAndJobId(deliveryId, jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return manualQaPlanService.getByDeliveryId(deliveryId);
    }

    @Override
    @Transactional
    public ProjectDeliveryResponse requestDeliveryRevision(
            UUID clientId, UUID jobId, UUID deliveryId, DeliveryRevisionRequest request) {
        Job job = getAccessibleJobForUpdate(clientId, jobId);
        Contract contract = requireContract(jobId);
        if (!contract.getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Dự án không còn chờ nghiệm thu");
        }
        ProjectDelivery delivery =
                deliveryRepository
                        .findByIdAndJobIdForUpdate(deliveryId, jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (delivery.getStatus() != DeliveryStatus.SUBMITTED) {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Phiên bản bàn giao không thể yêu cầu chỉnh sửa");
        }

        delivery.setStatus(DeliveryStatus.REVISION_REQUESTED);
        delivery.setRevisionReason(request.getReason().trim());
        delivery.setReviewedAt(LocalDateTime.now());
        return toDeliveryResponse(deliveryRepository.save(delivery));
    }

    @Override
    @Transactional
    public DeliveryAcceptanceResponse acceptDelivery(UUID clientId, UUID jobId, UUID deliveryId) {
        Job job = getAccessibleJobForUpdate(clientId, jobId);
        Contract contract = requireContract(jobId);
        if (!contract.getClient().getId().equals(clientId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new ApiException(ErrorCode.REQUEST_FAILED, "Dự án không còn chờ nghiệm thu");
        }
        ProjectDelivery delivery =
                deliveryRepository
                        .findByIdAndJobIdForUpdate(deliveryId, jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (delivery.getStatus() != DeliveryStatus.SUBMITTED) {
            throw new ApiException(
                    ErrorCode.REQUEST_FAILED, "Phiên bản bàn giao không thể nghiệm thu");
        }
        manualQaPlanService.getByDeliveryId(deliveryId);

        delivery.setStatus(DeliveryStatus.ACCEPTED);
        delivery.setReviewedAt(LocalDateTime.now());
        ProjectDelivery savedDelivery = deliveryRepository.save(delivery);
        return DeliveryAcceptanceResponse.builder()
                .delivery(toDeliveryResponse(savedDelivery))
                .settlement(sandboxSettlementService.settle(contract.getId(), savedDelivery))
                .build();
    }

    private List<ProjectTimelineTask> initializeTimeline(Job job) {
        List<ProjectTimelineTask> tasks =
                SrsTimelineParser.parse(job.getSrsContent()).stream()
                        .map(
                                parsed ->
                                        ProjectTimelineTask.builder()
                                                .job(job)
                                                .weekNumber(parsed.weekNumber())
                                                .sortOrder(parsed.sortOrder())
                                                .title(parsed.title())
                                                .description(parsed.description())
                                                .build())
                        .toList();
        return taskRepository.saveAll(tasks);
    }

    private ProjectTimelineResponse toTimelineResponse(Job job, List<ProjectTimelineTask> tasks) {
        List<UUID> taskIds = tasks.stream().map(ProjectTimelineTask::getId).toList();
        Map<UUID, List<ProjectTaskReport>> reportsByTask =
                taskIds.isEmpty()
                        ? Map.of()
                        : reportRepository.findAllByTaskIdInOrderByCreatedAtDesc(taskIds).stream()
                                .collect(
                                        Collectors.groupingBy(
                                                report -> report.getTask().getId(),
                                                LinkedHashMap::new,
                                                Collectors.toList()));

        List<TimelineTaskResponse> taskResponses =
                tasks.stream()
                        .map(
                                task ->
                                        toTaskResponse(
                                                task,
                                                reportsByTask.getOrDefault(
                                                        task.getId(), List.of())))
                        .toList();
        int progress =
                tasks.isEmpty()
                        ? 0
                        : (int)
                                Math.round(
                                        tasks.stream()
                                                .mapToInt(ProjectTimelineTask::getProgress)
                                                .average()
                                                .orElse(0));
        int totalWeeks =
                tasks.stream().mapToInt(ProjectTimelineTask::getWeekNumber).max().orElse(0);
        int currentWeek =
                tasks.stream()
                        .filter(task -> task.getProgress() < 100)
                        .mapToInt(ProjectTimelineTask::getWeekNumber)
                        .min()
                        .orElse(totalWeeks);
        boolean readyForDelivery =
                !tasks.isEmpty()
                        && tasks.stream()
                                .allMatch(
                                        task ->
                                                task.getProgress() == 100
                                                        && (task.getStatus()
                                                                        == ProjectTaskStatus
                                                                                .SUBMITTED
                                                                || task.getStatus()
                                                                        == ProjectTaskStatus
                                                                                .ACCEPTED));

        return ProjectTimelineResponse.builder()
                .jobId(job.getId())
                .jobTitle(job.getTitle())
                .progress(progress)
                .currentWeek(currentWeek)
                .totalWeeks(totalWeeks)
                .readyForDelivery(readyForDelivery)
                .tasks(taskResponses)
                .build();
    }

    private TimelineTaskResponse toTaskResponse(
            ProjectTimelineTask task, List<ProjectTaskReport> reports) {
        return TimelineTaskResponse.builder()
                .id(task.getId())
                .weekNumber(task.getWeekNumber())
                .sortOrder(task.getSortOrder())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .progress(task.getProgress())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .reports(reports.stream().map(this::toReportResponse).toList())
                .build();
    }

    private TaskReportResponse toReportResponse(ProjectTaskReport report) {
        return TaskReportResponse.builder()
                .id(report.getId())
                .reporterId(report.getReporter().getId())
                .reporterName(report.getReporter().getFullName())
                .content(report.getContent())
                .evidenceUrl(report.getEvidenceUrl())
                .progress(report.getProgress())
                .status(report.getReportedStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private ProjectDeliveryResponse toDeliveryResponse(ProjectDelivery delivery) {
        return ProjectDeliveryResponse.builder()
                .id(delivery.getId())
                .jobId(delivery.getJob().getId())
                .submittedBy(delivery.getSubmittedBy().getId())
                .submitterName(delivery.getSubmittedBy().getFullName())
                .versionNumber(delivery.getVersionNumber())
                .webUrl(delivery.getWebUrl())
                .repositoryUrl(delivery.getRepositoryUrl())
                .note(delivery.getNote())
                .status(delivery.getStatus())
                .revisionReason(delivery.getRevisionReason())
                .submittedAt(delivery.getSubmittedAt())
                .reviewedAt(delivery.getReviewedAt())
                .build();
    }

    private Job getAccessibleJob(UUID userId, UUID jobId) {
        Job job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        Contract contract = requireContract(jobId);
        boolean isParticipant =
                contract.getClient().getId().equals(userId)
                        || contract.getFreelancer().getId().equals(userId);
        if (!isParticipant && !SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        return job;
    }

    private Job getAccessibleJobForUpdate(UUID userId, UUID jobId) {
        Job job =
                jobRepository
                        .findByIdForWorkflowUpdate(jobId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        Contract contract = requireContract(jobId);
        boolean isParticipant =
                contract.getClient().getId().equals(userId)
                        || contract.getFreelancer().getId().equals(userId);
        if (!isParticipant && !SecurityUtils.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        return job;
    }

    private Contract requireContract(UUID jobId) {
        return contractRepository
                .findByJobId(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
