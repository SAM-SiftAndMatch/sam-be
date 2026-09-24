package com.sam.be.modules.ai.service.impl;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.client.GeminiClient;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.storage.service.StorageService;
import com.sam.be.modules.ai.dto.request.AiChatRequest;
import com.sam.be.modules.ai.dto.response.AiChatResponse;
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

    private String baSystemPrompt;
    private String riskSystemPrompt;
    private String skillMatchingPrompt;
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
        // LUỒNG 1 (BA): DÙNG REDIS ĐỂ LƯU LỊCH SỬ HỘI THOẠI
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
        // LUỒNG 2 (RISK): KHÔNG DÙNG REDIS, GỬI MỚI HOÀN TOÀN MỖI LƯỢT
        String userPrompt = String.format(
                "TÀI LIỆU SRS HIỆN TẠI TỪ KHÁCH HÀNG:\n%s\n\n" +
                        "YÊU CẦU CỦA KHÁCH HÀNG (Sửa ngân sách/thời gian/tính năng): %s",
                request.getCurrentSrsContent() != null ? request.getCurrentSrsContent() : "(Chưa có)",
                request.getUserMessage()
        );

        List<Map<String, Object>> singleTurnHistory = new ArrayList<>();
        singleTurnHistory.add(createMessage("user", userPrompt));

        // Chỉ gửi đi đúng 1 tin nhắn này, không kéo theo lịch sử cũ
        String aiResponseJson = geminiClient.generateContent(riskSystemPrompt, singleTurnHistory);

        return parseAndUploadSrs(request.getSessionId(), aiResponseJson);
    }

    @Override
    public List<Integer> extractSkillIdsForJob(String srsContent, String availableSkillsJson) {
        String userMessage = String.format("TÀI LIỆU SRS:\n%s\n\nDANH SÁCH SKILLS:\n%s", srsContent, availableSkillsJson);
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(createMessage("user", userMessage));

        String aiResponseJson = geminiClient.generateContent(skillMatchingPrompt, history);

        try {
            return objectMapper.readValue(aiResponseJson, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            log.error("AI Skill Matcher failed. Raw: {}", aiResponseJson, e);
            return new ArrayList<>();
        }
    }

    // Hàm dùng chung để tách logic Parse JSON và Upload Cloudinary ra khỏi các luồng Chat
    private AiChatResponse parseAndUploadSrs(String sessionId, String aiResponseJson) {
        try {
            ObjectMapper permissiveMapper = objectMapper.copy()
                    .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);

            AiChatResponse response = permissiveMapper.readValue(aiResponseJson, AiChatResponse.class);

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
            try {
                return objectMapper.readValue(data, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                log.warn("Failed to load history from Redis", e);
            }
        }
        return new ArrayList<>();
    }

    private void saveHistory(String key, List<Map<String, Object>> history) {
        try {
            String data = objectMapper.writeValueAsString(history);
            stringRedisTemplate.opsForValue().set(key, data, Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Failed to save history to Redis", e);
        }
    }

    private Map<String, Object> createMessage(String role, String text) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", role);
        message.put("parts", List.of(Map.of("text", text)));
        return message;
    }
}