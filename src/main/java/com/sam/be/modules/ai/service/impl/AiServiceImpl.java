package com.sam.be.modules.ai.service.impl;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.client.GeminiClient;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.storage.service.StorageService;
import com.sam.be.modules.ai.dto.request.AiChatRequest;
import com.sam.be.modules.ai.dto.response.*;
import com.sam.be.modules.ai.service.AiService;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private static final String BA_REDIS_PREFIX = "ai:ba:session:";

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

    @Value("classpath:prompts/contract_generator_prompt.txt")
    private Resource contractPromptResource;

    @Value("classpath:prompts/contract_amount_verify_prompt.txt")
    private Resource amountVerifyPromptResource;

    @Value("classpath:prompts/contract_amount_review_prompt.txt")
    private Resource amountReviewPromptResource;

    private String baSystemPrompt;
    private String riskSystemPrompt;
    private String skillMatchingPrompt;
    private String candidateEvaluationPrompt;
    private String contractSystemPrompt;
    private String amountVerifyPrompt;
    private String amountReviewPrompt;
    private List<AiQuestion> baseQuestions;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final GeminiClient geminiClient;
    private final StorageService storageService;

    @PostConstruct
    public void init() {
        try {
            baSystemPrompt =
                    StreamUtils.copyToString(
                            baPromptResource.getInputStream(), StandardCharsets.UTF_8);
            riskSystemPrompt =
                    StreamUtils.copyToString(
                            riskPromptResource.getInputStream(), StandardCharsets.UTF_8);
            skillMatchingPrompt =
                    StreamUtils.copyToString(
                            skillMatchingPromptResource.getInputStream(), StandardCharsets.UTF_8);
            candidateEvaluationPrompt =
                    StreamUtils.copyToString(
                            candidateEvaluationPromptResource.getInputStream(),
                            StandardCharsets.UTF_8);
            contractSystemPrompt =
                    StreamUtils.copyToString(
                            contractPromptResource.getInputStream(), StandardCharsets.UTF_8);
            amountVerifyPrompt =
                    StreamUtils.copyToString(
                            amountVerifyPromptResource.getInputStream(), StandardCharsets.UTF_8);
            amountReviewPrompt =
                    StreamUtils.copyToString(
                            amountReviewPromptResource.getInputStream(), StandardCharsets.UTF_8);
            String questionsJson =
                    StreamUtils.copyToString(
                            baseQuestionsResource.getInputStream(), StandardCharsets.UTF_8);
            baseQuestions =
                    objectMapper.readValue(questionsJson, new TypeReference<List<AiQuestion>>() {});
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
        return parseAndUploadSrs(request.getSessionId(), aiResponseJson, true);
    }

    @Override
    public AiChatResponse chatWithAiRisk(AiChatRequest request) {
        StringBuilder userPrompt = new StringBuilder();

        // Nếu có lịch sử hội thoại thì đưa vào để AI nhớ các option đã đề xuất
        if (request.getChatHistory() != null && !request.getChatHistory().isEmpty()) {
            userPrompt.append("LỊCH SỬ ĐÀM PHÁN TRƯỚC ĐÓ (Quan trọng: phải bám sát các option đã đề xuất):\n");
            for (Map<String, String> msg : request.getChatHistory()) {
                String role = msg.getOrDefault("role", "user");
                String content = msg.getOrDefault("content", "");
                userPrompt.append(role).append(": ").append(content).append("\n");
            }
            userPrompt.append("\n");
        }

        userPrompt.append(
                String.format(
                        "TÀI LIỆU SRS HIỆN TẠI TỪ KHÁCH HÀNG:\n%s\n\nYÊU CẦU MỚI CỦA KHÁCH HÀNG: %s",
                        request.getCurrentSrsContent() != null
                                ? request.getCurrentSrsContent()
                                : "(Chưa có)",
                        request.getUserMessage()));

        List<Map<String, Object>> singleTurnHistory = new ArrayList<>();
        singleTurnHistory.add(createMessage("user", userPrompt.toString()));
        String aiResponseJson = geminiClient.generateContent(riskSystemPrompt, singleTurnHistory);
        return parseAndUploadSrs(request.getSessionId(), aiResponseJson, false);
    }

    @Override
    public List<AiExtractedSkill> extractSkillsForJob(
            String srsContent, String availableSkillsJson) {
        String userMessage =
                String.format(
                        "TÀI LIỆU SRS:\n%s\n\nDANH SÁCH SKILLS:\n%s",
                        srsContent, availableSkillsJson);
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(createMessage("user", userMessage));

        String aiResponseJson = geminiClient.generateContent(skillMatchingPrompt, history);
        String cleanedJson = cleanJson(aiResponseJson);

        try {
            if (!cleanedJson.startsWith("[")) {
                cleanedJson = "[" + cleanedJson + "]";
            }
            return objectMapper.readValue(
                    cleanedJson, new TypeReference<List<AiExtractedSkill>>() {});
        } catch (Exception e) {
            log.error("AI Skill Matcher failed. Raw: {}", aiResponseJson, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<AiCandidateScore> evaluateCandidates(String srsContent, String candidatesJson) {
        String userMessage =
                String.format(
                        "TÀI LIỆU SRS:\n%s\n\nHỒ SƠ ỨNG VIÊN:\n%s", srsContent, candidatesJson);
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(createMessage("user", userMessage));

        String aiResponseJson = geminiClient.generateContent(candidateEvaluationPrompt, history);
        String cleanedJson = cleanJson(aiResponseJson);

        try {
            if (!cleanedJson.startsWith("[")) {
                cleanedJson = "[" + cleanedJson + "]";
            }
            return objectMapper.readValue(
                    cleanedJson, new TypeReference<List<AiCandidateScore>>() {});
        } catch (Exception e) {
            log.error("AI Candidate Evaluation failed. Raw: {}", aiResponseJson, e);
            return new ArrayList<>();
        }
    }

    @Override
    public AiContractDraft generateContractDraft(
            String srsContent,
            BigDecimal minBudget,
            BigDecimal maxBudget,
            String clientName,
            String freelancerName) {
        String userMessage =
                String.format(
                        "TÀI LIỆU SRS:\n%s\n\nNGÂN SÁCH DỰ KIẾN: Từ %s đến %s\n\nBÊN A (BÊN ĐẶT HÀNG): %s\nBÊN B (BÊN PHÁT TRIỂN): %s",
                        srsContent,
                        minBudget,
                        maxBudget,
                        clientName != null ? clientName : "Bên đặt hàng",
                        freelancerName != null ? freelancerName : "Bên phát triển");

        List<Map<String, Object>> history = new ArrayList<>();
        history.add(createMessage("user", userMessage));

        String aiResponseJson = geminiClient.generateContent(contractSystemPrompt, history);
        String cleanedJson = cleanJson(aiResponseJson);

        try {
            return objectMapper.readValue(cleanedJson, AiContractDraft.class);
        } catch (Exception e) {
            log.error("AI Contract Generation failed. Raw: {}", aiResponseJson, e);
            throw new ApiException(ErrorCode.UNEXPECTED_ERROR);
        }
    }

    @Override
    public AiAmountVerification verifyContractAmount(String contractTerms, BigDecimal systemAmount) {
        String userMessage =
                String.format(
                        "VĂN BẢN HỢP ĐỒNG:\n%s\n\nCON SỐ HỆ THỐNG: %s",
                        contractTerms != null ? contractTerms : "(trống)", systemAmount);
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(createMessage("user", userMessage));

        String aiResponseJson = geminiClient.generateContent(amountVerifyPrompt, history);
        String cleanedJson = cleanJson(aiResponseJson);

        AiAmountVerification result = new AiAmountVerification();
        try {
            result = objectMapper.readValue(cleanedJson, AiAmountVerification.class);
        } catch (Exception e) {
            log.error("AI Amount Verification failed. Raw: {}", aiResponseJson, e);
            result.setMatches(false);
            result.setNote("AI không kiểm chứng được con số, vui lòng thử lại.");
            return result;
        }
        if (result.getExtractedAmount() == null || !Boolean.TRUE.equals(result.getMatches())) {
            result.setMatches(false);
            if (result.getNote() == null || result.getNote().isBlank()) {
                result.setNote("Số tiền trong văn bản không khớp với giá thỏa thuận.");
            }
        }
        return result;
    }

    @Override
    public AiContractReview reviewContractTerms(
            String contractTerms, BigDecimal agreedAmount, BigDecimal initialAmount) {
        String userMessage =
                String.format(
                        "VĂN BẢN HỢP ĐỒNG:\n%s\n\nGIÁ THỎA THUẬN TRÊN HỆ THỐNG (agreedAmount): %s\nGIÁ AI ĐỀ XUẤT BAN ĐẦU (initialAmount): %s",
                        contractTerms != null ? contractTerms : "(trống)",
                        agreedAmount,
                        initialAmount != null ? initialAmount.toString() : "(không có)");
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(createMessage("user", userMessage));

        String aiResponseJson = geminiClient.generateContent(amountReviewPrompt, history);
        String cleanedJson = cleanJson(aiResponseJson);

        try {
            AiContractReview review = objectMapper.readValue(cleanedJson, AiContractReview.class);
            // Chuẩn hóa verdict lạ về NEEDS_CONFIRM để 2 bên chốt tay cho chắc
            if (!"OK".equalsIgnoreCase(review.getVerdict())) {
                review.setVerdict("NEEDS_CONFIRM");
                if (review.getChatMessage() == null || review.getChatMessage().isBlank()) {
                    review.setChatMessage(
                            "AI phát hiện điểm cần xem lại trong hợp đồng. Hai bên kiểm tra kỹ: nếu giữ nguyên bản này thì bấm \"Giữ nguyên\", còn không thì sửa lại rồi ký lại để thẩm định tiếp.");
                }
            } else {
                review.setVerdict("OK");
                if (review.getChatMessage() == null || review.getChatMessage().isBlank()) {
                    review.setChatMessage(
                            "AI đã thẩm định xong: hợp đồng hợp lệ, số tiền khớp. Hai bên nạp tiền để dự án bắt đầu nhé!");
                }
            }
            return review;
        } catch (Exception e) {
            log.error("AI Contract Review failed. Raw: {}", aiResponseJson, e);
            // AI lỗi kỹ thuật: không chặn chết — nhờ 2 bên kiểm tra tay rồi bấm Giữ nguyên
            AiContractReview fallback = new AiContractReview();
            fallback.setVerdict("NEEDS_CONFIRM");
            fallback.setChatMessage(
                    "AI tạm thời không thẩm định được (lỗi kỹ thuật). Hai bên tự kiểm tra kỹ số tiền và điều khoản: nếu giữ nguyên bản này thì bấm \"Giữ nguyên\", còn không thì sửa lại rồi ký lại.");
            return fallback;
        }
    }

    private String cleanJson(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    private AiChatResponse parseAndUploadSrs(
            String sessionId, String aiResponseJson, boolean requireCompletedStatus) {
        try {
            ObjectMapper permissiveMapper =
                    objectMapper
                            .copy()
                            .configure(
                                    JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(),
                                    true);

            String cleaned = cleanJson(aiResponseJson);
            AiChatResponse response = permissiveMapper.readValue(cleaned, AiChatResponse.class);

            if (response.getSrsContent() != null) {
                // Gemini đôi khi trả về hai ký tự literal "\\n" thay vì newline thật.
                // Chuẩn hóa trước khi lưu/upload để Markdown giữ đúng đoạn, heading và list.
                String normalizedSrs =
                        response.getSrsContent()
                                .replace("\\\\r\\\\n", "\n")
                                .replace("\\\\n", "\n")
                                .replace("\\\\r", "\n")
                                .replace("\\r\\n", "\n")
                                .replace("\\n", "\n");
                response.setSrsContent(normalizedSrs);
            }

            boolean isCompleted = "COMPLETED".equalsIgnoreCase(response.getStatus());
            if (requireCompletedStatus && !isCompleted) {
                // Guard against the model emitting an SRS before completing the mandatory interview.
                response.setSrsContent(null);
                response.setCurrentSrsUrl(null);
                response.setRiskLevel(null);
                return response;
            }

            if (response.getSrsContent() != null && !response.getSrsContent().trim().isEmpty()) {
                String fileName = "srs-" + sessionId + "-" + System.currentTimeMillis();
                String secureUrl =
                        storageService.uploadMarkdown(response.getSrsContent(), fileName);
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
                return objectMapper.readValue(
                        data, new TypeReference<List<Map<String, Object>>>() {});
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
