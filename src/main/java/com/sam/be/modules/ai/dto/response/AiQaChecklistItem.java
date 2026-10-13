package com.sam.be.modules.ai.dto.response;

import lombok.Data;

@Data
public class AiQaChecklistItem {
    private String requirementRef;
    private String title;
    private String testSteps;
    private String expectedResult;
    private String priority;
}
