package com.sam.be.modules.workflow.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class ProjectTimelineResponse {
    private UUID jobId;
    private String jobTitle;
    private Integer progress;
    private Integer currentWeek;
    private Integer totalWeeks;
    private Boolean readyForDelivery;
    private List<TimelineTaskResponse> tasks;
}
