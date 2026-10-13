package com.sam.be.modules.workflow.dto.request;

import com.sam.be.common.constant.enums.ProjectTaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TaskReportRequest {
    @NotBlank private String content;

    @Pattern(regexp = "^https?://.+", message = "Evidence URL must use http or https")
    private String evidenceUrl;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer progress;

    @NotNull private ProjectTaskStatus status;
}
