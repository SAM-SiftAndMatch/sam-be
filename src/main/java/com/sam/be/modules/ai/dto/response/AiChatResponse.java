package com.sam.be.modules.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private String status;
    private String aiMessage;
    private List<AiQuestion> questions;
    private String srsContent;
    private String currentSrsUrl;
    private String riskLevel;
}