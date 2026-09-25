package com.sam.be.modules.ai.service;

import com.sam.be.modules.ai.dto.request.AiChatRequest;
import com.sam.be.modules.ai.dto.response.AiCandidateScore;
import com.sam.be.modules.ai.dto.response.AiChatResponse;
import com.sam.be.modules.ai.dto.response.AiExtractedSkill;
import com.sam.be.modules.ai.dto.response.AiQuestion;
import java.util.List;

public interface AiService {
    List<AiQuestion> getBaseQuestions();
    AiChatResponse chatWithAiBa(AiChatRequest request);
    AiChatResponse chatWithAiRisk(AiChatRequest request);
    List<AiExtractedSkill> extractSkillsForJob(String srsContent, String availableSkillsJson);
    List<AiCandidateScore> evaluateCandidates(String srsContent, String candidatesJson);
}