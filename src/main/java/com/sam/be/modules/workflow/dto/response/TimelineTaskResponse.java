package com.sam.be.modules.workflow.dto.response;

import com.sam.be.common.constant.enums.ProjectTaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class TimelineTaskResponse {
    private UUID id;
    private Integer weekNumber;
    private Integer sortOrder;
    private String title;
    private String description;
    private ProjectTaskStatus status;
    private Integer progress;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private List<TaskReportResponse> reports;
}
