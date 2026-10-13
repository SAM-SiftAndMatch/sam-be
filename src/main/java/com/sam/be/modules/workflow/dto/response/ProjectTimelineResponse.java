package com.sam.be.modules.workflow.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectTimelineResponse {
    private UUID jobId;
    private String jobTitle;
    private Integer progress;
    private Integer currentWeek;
    private Integer totalWeeks;
    private Boolean readyForDelivery;
    private List<TimelineTaskResponse> tasks;
}
