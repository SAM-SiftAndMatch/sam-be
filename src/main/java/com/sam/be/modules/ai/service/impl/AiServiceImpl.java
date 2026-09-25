package com.sam.be.modules.ai.service.impl;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.client.GeminiClient;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.storage.service.StorageService;
import com.sam.be.modules.ai.dto.request.AiChatRequest;
import com.sam.be.modules.ai.dto.response.AiCandidateScore;
import com.sam.be.modules.ai.dto.response.AiChatResponse;
import com.sam.be.modules.ai.dto.response.AiExtractedSkill;
import com.sam.be.modules.ai.dto.response.AiQuestion;
import com.sam.be.modules.ai.service.AiService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    @Value("classpath:prompts/ba_system_prompt.txt")
    private Resource baPromptResource;

    @Value("classpath:prompts/risk_system_prompt.txt")
    private Resource riskPromptResource;

    @Value("classpath:prompts/base_questions.json")
    private Resource baseQuestionsResource;

    @Value("classpath:prompts/skill_matching_prompt.txt")
    private Resource skillMatchingPromptResource;

    @Value("classpath:prompts/candidate_evaluation_prompt.txt")
    private Resource candidateEvaluationPromptResource;

    private String baSystemPrompt;
    private String riskSystemPrompt;
    private String skillMatchingPrompt;
    private String candidateEvaluationPrompt;
    private List<AiQuestion> baseQuestions;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final GeminiClient geminiClient;
    private final StorageService storageService;

    private static final String BA_REDIS_PREFIX = "ai:ba:session:";

    @PostConstruct
    public void init() {
        try {
            baSystemPrompt = StreamUtils.copyToString(baPromptResource.getInputStream(), StandardCharsets.UTF_8);
            riskSystemPrompt = StreamUtils.copyToString(riskPromptResource.getInputStream(), StandardCharsets.UTF_8);
            skillMatchingPrompt = StreamUtils.copyToString(skillMatchingPromptResource.getInputStream(), StandardCharsets.UTF_8);
            candidateEvaluationPrompt = StreamUtils.copyToString(candidateEvaluationPromptResource.getInputStream(), StandardCharsets.UTF_8);
            String questionsJson = StreamUtils.copyToString(baseQuestionsResource.getInputStream(), StandardCharsets.UTF_8);
            baseQuestions = objectMapper.readValue(questionsJson, new TypeReference<List<AiQuestion>>() {});
        } catch (Exception e) {
            log.error("Failed to load AI resources", e);
            throw new RuntimeException("Failed to load AI resources", e);
        }
    }

    @Override
    public List<AiQuestion> getBaseQuestions() {
        return baseQuestions;
    }

    @Override
    public AiChatResponse chatWithAiBa(AiChatRequest request) {
        String redisKey = BA_REDIS_PREFIX + request.getSessionId();
        List<Map<String, Object>> history = loadHistory(redisKey);
        history.add(createMessage("user", request.getUserMessage()));
        String aiResponseJson = geminiClient.generateContent(baSystemPrompt, history);
        history.add(createMessage("model", aiResponseJson));
        saveHistory(redisKey, history);
        return parseAndUploadSrs(request.getSessionId(), aiResponseJson);
    }

    @Override
    public AiChatResponse chatWithAiRisk(AiChatRequest request) {
        String userPrompt = String.format(
                "TÀI LIỆU SRS HIỆN TẠI TỪ KHÁCH HÀNG:\n%s\n\nYÊU CẦU CỦA KHÁCH HÀNG: %s",
                request.getCurrentSrsContent() != null ? request.getCurrentSrsContent() : "(Chưa có)",
                request.getUserMessage()
        );
        List<Map<String, Object>> singleTurnHistory = new ArrayList<>();
        singleTurnHistory.add(createMessage("user", userPrompt));
        String aiResponseJson = geminiClient.generateContent(riskSystemPrompt, singleTurnHistory);
        return parseAndUploadSrs(request.getSessionId(), aiResponseJson);
    }

    @Override
    public List<AiExtractedSkill> extractSkillsForJob(String srsContent, String availableSkillsJson) {
        String userMessage = String.format("TÀI LIỆU SRS:\n%s\n\nDANH SÁCH SKILLS:\n%s", srsContent, availableSkillsJson);
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(createMessage("user", userMessage));

        String aiResponseJson = geminiClient.generateContent(skillMatchingPrompt, history);
        String cleanedJson = cleanJsonResponse(aiResponseJson);

        try {
            return objectMapper.readValue(cleanedJson, new TypeReference<List<AiExtractedSkill>>() {});
        } catch (Exception e) {
            log.error("AI Skill Matcher failed. Raw: {}", aiResponseJson, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<AiCandidateScore> evaluateCandidates(String srsContent, String candidatesJson) {
        String userMessage = String.format("TÀI LIỆU SRS:\n%s\n\nHỒ SƠ ỨNG VIÊN:\n%s", srsContent, candidatesJson);
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(createMessage("user", userMessage));

        String aiResponseJson = geminiClient.generateContent(candidateEvaluationPrompt, history);
        String cleanedJson = cleanJsonResponse(aiResponseJson);

        try {
            return objectMapper.readValue(cleanedJson, new TypeReference<List<AiCandidateScore>>() {});
        } catch (Exception e) {
            log.error("AI Candidate Evaluation failed. Raw: {}", aiResponseJson, e);
            return new ArrayList<>();
        }
    }

    // --- HÀM MỚI: Dọn dẹp JSON từ AI ---
    private String cleanJsonResponse(String raw) {
        if (raw == null) return "[]";
        String cleaned = raw.trim();

        // Loại bỏ markdown block nếu có
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        // Cứu hộ lỗi AI quên bọc ngoặc vuông: { ... }, { ... }
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            cleaned = "[" + cleaned + "]";
        }
        return cleaned;
    }

    private AiChatResponse parseAndUploadSrs(String sessionId, String aiResponseJson) {
        try {
            ObjectMapper permissiveMapper = objectMapper.copy()
                    .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);

            String cleaned = cleanJsonResponse(aiResponseJson);
            // Dành riêng cho Chat (trả về 1 object), nếu bị bọc mảng thì gỡ ra
            if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }

            AiChatResponse response = permissiveMapper.readValue(cleaned, AiChatResponse.class);

            if (response.getSrsContent() != null && !response.getSrsContent().trim().isEmpty()) {
                String fileName = "srs-" + sessionId + "-" + System.currentTimeMillis();
                String secureUrl = storageService.uploadMarkdown(response.getSrsContent(), fileName);
                response.setCurrentSrsUrl(secureUrl);
            }
            return response;
        } catch (Exception e) {
            log.error("JSON parse failed. Raw response: {}", aiResponseJson, e);
            throw new ApiException(ErrorCode.UNEXPECTED_ERROR);
        }
    }

    private List<Map<String, Object>> loadHistory(String key) {
        String data = stringRedisTemplate.opsForValue().get(key);
        if (data != null) {
            try { return objectMapper.readValue(data, new TypeReference<List<Map<String, Object>>>() {}); }
            catch (Exception e) { log.warn("Failed to load history from Redis", e); }
        }
        return new ArrayList<>();
    }

    private void saveHistory(String key, List<Map<String, Object>> history) {
        try {
            String data = objectMapper.writeValueAsString(history);
            stringRedisTemplate.opsForValue().set(key, data, Duration.ofHours(24));
        } catch (Exception e) { log.warn("Failed to save history to Redis", e); }
    }

    private Map<String, Object> createMessage(String role, String text) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", role);
        message.put("parts", List.of(Map.of("text", text)));
        return message;
    }
}