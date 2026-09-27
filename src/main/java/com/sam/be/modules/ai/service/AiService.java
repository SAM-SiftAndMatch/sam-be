package com.sam.be.modules.ai.service;

import com.sam.be.modules.ai.dto.request.AiChatRequest;
import com.sam.be.modules.ai.dto.response.*;

import java.math.BigDecimal;
import java.util.List;

public interface AiService {
    List<AiQuestion> getBaseQuestions();
    AiChatResponse chatWithAiBa(AiChatRequest request);
    AiChatResponse chatWithAiRisk(AiChatRequest request);
    List<AiExtractedSkill> extractSkillsForJob(String srsContent, String availableSkillsJson);
    List<AiCandidateScore> evaluateCandidates(String srsContent, String candidatesJson);
    AiContractDraft generateContractDraft(String srsContent, BigDecimal minBudget, BigDecimal maxBudget);
}