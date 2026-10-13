package com.sam.be.modules.workflow.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QaChecklistItemResponse {
    private UUID id;
    private Integer sortOrder;
    private String requirementRef;
    private String title;
    private String testSteps;
    private String expectedResult;
    private String priority;
}
