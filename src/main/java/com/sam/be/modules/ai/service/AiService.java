package com.sam.be.modules.ai.service;

import com.sam.be.modules.ai.dto.request.AiChatRequest;
import com.sam.be.modules.ai.dto.response.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AiService {
    List<AiQuestion> getBaseQuestions();

    AiChatResponse chatWithAiBa(AiChatRequest request);

    AiChatResponse chatWithAiBaFromHistory(AiChatRequest request, List<Map<String, Object>> history);

    AiChatResponse chatWithAiRisk(AiChatRequest request);

    List<AiExtractedSkill> extractSkillsForJob(String srsContent, String availableSkillsJson);

    List<AiCandidateScore> evaluateCandidates(String srsContent, String candidatesJson);

    AiContractDraft generateContractDraft(
            String srsContent,
            BigDecimal minBudget,
            BigDecimal maxBudget,
            String clientName,
            String freelancerName);

    AiAmountVerification verifyContractAmount(String contractTerms, BigDecimal systemAmount);

    AiContractReview reviewContractTerms(
            String contractTerms, BigDecimal agreedAmount, BigDecimal initialAmount);

    AiQaPlan generateManualQaPlan(String srsContent, String timelineSummary, String deliveryUrl);
}
