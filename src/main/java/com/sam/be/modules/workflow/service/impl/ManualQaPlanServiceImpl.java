package com.sam.be.modules.workflow.service.impl;

import com.sam.be.common.constant.enums.QaPlanStatus;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.ai.dto.response.AiQaChecklistItem;
import com.sam.be.modules.ai.dto.response.AiQaPlan;
import com.sam.be.modules.ai.service.AiService;
import com.sam.be.modules.workflow.dto.response.QaChecklistItemResponse;
import com.sam.be.modules.workflow.dto.response.QaPlanResponse;
import com.sam.be.modules.workflow.entity.ProjectDelivery;
import com.sam.be.modules.workflow.entity.ProjectTimelineTask;
import com.sam.be.modules.workflow.entity.QaChecklistItem;
import com.sam.be.modules.workflow.entity.QaPlan;
import com.sam.be.modules.workflow.repository.QaChecklistItemRepository;
import com.sam.be.modules.workflow.repository.QaPlanRepository;
import com.sam.be.modules.workflow.service.ManualQaPlanService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManualQaPlanServiceImpl implements ManualQaPlanService {

    private final QaPlanRepository qaPlanRepository;
    private final QaChecklistItemRepository checklistItemRepository;
    private final AiService aiService;

    @Override
    @Transactional
    public QaPlanResponse generateForDelivery(
            ProjectDelivery delivery, String srsContent, List<ProjectTimelineTask> tasks) {
        return qaPlanRepository
                .findByDeliveryId(delivery.getId())
                .map(this::toResponse)
                .orElseGet(() -> createPlan(delivery, srsContent, tasks));
    }

    @Override
    @Transactional(readOnly = true)
    public QaPlanResponse getByDeliveryId(UUID deliveryId) {
        QaPlan plan =
                qaPlanRepository
                        .findByDeliveryId(deliveryId)
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        return toResponse(plan);
    }

    private QaPlanResponse createPlan(
            ProjectDelivery delivery, String srsContent, List<ProjectTimelineTask> tasks) {
        String timelineSummary =
                tasks.stream()
                        .map(task -> "Tuần " + task.getWeekNumber() + ": " + task.getTitle())
                        .reduce("", (left, right) -> left + "\n" + right)
                        .trim();
        AiQaPlan aiPlan =
                aiService.generateManualQaPlan(srsContent, timelineSummary, delivery.getWebUrl());
        boolean hasAiPlan =
                aiPlan.getChecklistItems() != null && !aiPlan.getChecklistItems().isEmpty();
        QaPlan plan =
                qaPlanRepository.save(
                        QaPlan.builder()
                                .delivery(delivery)
                                .sourceSrs(srsContent)
                                .status(hasAiPlan ? QaPlanStatus.GENERATED : QaPlanStatus.FALLBACK)
                                .generatedAt(LocalDateTime.now())
                                .build());
        List<AiQaChecklistItem> items =
                hasAiPlan ? aiPlan.getChecklistItems() : fallbackItems(tasks);
        checklistItemRepository.saveAll(
                IntStream.range(0, Math.min(items.size(), 15))
                        .mapToObj(index -> toEntity(plan, index + 1, items.get(index)))
                        .toList());
        return toResponse(plan);
    }

    private List<AiQaChecklistItem> fallbackItems(List<ProjectTimelineTask> tasks) {
        return tasks.stream()
                .map(
                        task -> {
                            AiQaChecklistItem item = new AiQaChecklistItem();
                            item.setRequirementRef("Tuần " + task.getWeekNumber());
                            item.setTitle(task.getTitle());
                            item.setTestSteps(
                                    "1. Mở website bàn giao.\n2. Thực hiện luồng tương ứng với mốc: "
                                            + task.getTitle()
                                            + ".\n3. Đối chiếu với mô tả trong SRS.");
                            item.setExpectedResult(
                                    "Chức năng hoạt động theo đúng mốc công việc và không có lỗi hiển thị rõ ràng.");
                            item.setPriority("HIGH");
                            return item;
                        })
                .toList();
    }

    private QaChecklistItem toEntity(QaPlan plan, int sortOrder, AiQaChecklistItem item) {
        return QaChecklistItem.builder()
                .qaPlan(plan)
                .sortOrder(sortOrder)
                .requirementRef(limit(item.getRequirementRef(), 100))
                .title(limit(defaultText(item.getTitle(), "Kiểm tra chức năng"), 500))
                .testSteps(
                        defaultText(
                                item.getTestSteps(), "Mở website bàn giao và thực hiện chức năng."))
                .expectedResult(
                        defaultText(item.getExpectedResult(), "Chức năng hoạt động đúng theo SRS."))
                .priority(normalizePriority(item.getPriority()))
                .build();
    }

    private QaPlanResponse toResponse(QaPlan plan) {
        return QaPlanResponse.builder()
                .id(plan.getId())
                .deliveryId(plan.getDelivery().getId())
                .status(plan.getStatus())
                .generatedAt(plan.getGeneratedAt())
                .checklistItems(
                        checklistItemRepository
                                .findAllByQaPlanIdOrderBySortOrderAsc(plan.getId())
                                .stream()
                                .map(
                                        item ->
                                                QaChecklistItemResponse.builder()
                                                        .id(item.getId())
                                                        .sortOrder(item.getSortOrder())
                                                        .requirementRef(item.getRequirementRef())
                                                        .title(item.getTitle())
                                                        .testSteps(item.getTestSteps())
                                                        .expectedResult(item.getExpectedResult())
                                                        .priority(item.getPriority())
                                                        .build())
                                .toList())
                .build();
    }

    private String normalizePriority(String priority) {
        if ("CRITICAL".equalsIgnoreCase(priority) || "HIGH".equalsIgnoreCase(priority)) {
            return priority.toUpperCase();
        }
        return "MEDIUM";
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
