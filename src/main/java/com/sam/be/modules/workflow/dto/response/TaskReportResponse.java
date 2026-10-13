package com.sam.be.modules.workflow.dto.response;

import com.sam.be.common.constant.enums.ProjectTaskStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class TaskReportResponse {
    private UUID id;
    private UUID reporterId;
    private String reporterName;
    private String content;
    private String evidenceUrl;
    private Integer progress;
    private ProjectTaskStatus status;
    private LocalDateTime createdAt;
}
