package com.sam.be.modules.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.client.GeminiClient;
import com.sam.be.common.storage.service.StorageService;
import com.sam.be.modules.ai.dto.request.AiChatRequest;
import com.sam.be.modules.ai.dto.response.AiChatResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

class AiServiceImplTest {
    @org.junit.jupiter.api.BeforeEach
    void authenticate() {
        var jwt =
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-token")
                        .header("alg", "HS256")
                        .claim("user_id", "00000000-0000-0000-0000-000000000001")
                        .build();
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(
                        new org.springframework.security.oauth2.server.resource.authentication
                                .JwtAuthenticationToken(jwt));
    }

    @org.junit.jupiter.api.AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void firstBriefReturnsContextualQuestionsWithoutCreatingSrs() {
        Fixture fixture = new Fixture();
        when(fixture.geminiClient.generateContent(anyString(), anyList()))
                .thenReturn(
                        """
                        {"status":"ASKING","aiMessage":"Tôi cần hỏi thêm về lịch khám.","questions":[{"id":"booking","type":"TEXT","questionText":"Bệnh nhân đặt lịch bằng cách nào?","options":[],"allowCustomInput":true,"hint":"Mô tả luồng đặt lịch."}],"srsContent":null}
                        """);

        AiChatResponse response = fixture.service.chatWithAiBa(fixture.request());

        assertEquals("ASKING", response.getStatus());
        assertEquals(
                "Bệnh nhân đặt lịch bằng cách nào?",
                response.getQuestions().get(0).getQuestionText());
        assertNull(response.getSrsContent());
        verify(fixture.storageService, never()).uploadMarkdown(anyString(), anyString());
    }

    @Test
    void firstBriefRejectsPrematureSrsAndStoresGuardedReply() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.geminiClient.generateContent(anyString(), anyList()))
                .thenReturn(
                        """
                        {"status":"COMPLETED","aiMessage":"Đã hoàn thành.","questions":[],"srsContent":"# SRS","exactBudgetVnd":1000000,"durationMonths":1}
                        """);

        AiChatResponse response = fixture.service.chatWithAiBa(fixture.request());

        assertEquals("ASKING", response.getStatus());
        assertFalse(response.getQuestions().isEmpty());
        assertNull(response.getSrsContent());
        assertNull(response.getExactBudgetVnd());
        verify(fixture.storageService, never()).uploadMarkdown(anyString(), anyString());

        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(fixture.values)
                .set(
                        eq("ai:ba:session:00000000-0000-0000-0000-000000000001:test-session"),
                        saved.capture(),
                        eq(Duration.ofHours(24)));
        JsonNode history = fixture.mapper.readTree(saved.getValue());
        String modelReply = history.get(1).get("parts").get(0).get("text").asText();
        assertEquals("ASKING", fixture.mapper.readTree(modelReply).get("status").asText());
    }

    @Test
    void laterTurnCanCompleteSrsAfterQuestions() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.values.get("ai:ba:session:00000000-0000-0000-0000-000000000001:test-session"))
                .thenReturn(
                        """
                        [{"role":"user","parts":[{"text":"Tôi cần website đặt lịch khám"}]},{"role":"model","parts":[{"text":"{\\"status\\":\\"ASKING\\"}"}]}]
                        """);
        when(fixture.geminiClient.generateContent(anyString(), anyList()))
                .thenReturn(
                        """
                        {"status":"COMPLETED","aiMessage":"Đã hoàn thành.","questions":[],"srsContent":"# SRS","exactBudgetVnd":1000000,"durationMonths":1}
                        """);
        when(fixture.storageService.uploadMarkdown(anyString(), anyString()))
                .thenReturn("https://storage.example/srs");

        AiChatResponse response = fixture.service.chatWithAiBa(fixture.request());

        assertEquals("COMPLETED", response.getStatus());
        assertEquals("# SRS", response.getSrsContent());
        assertEquals("https://storage.example/srs", response.getCurrentSrsUrl());
        verify(fixture.storageService).uploadMarkdown(eq("# SRS"), anyString());
    }

    @Test
    void legacyRiskRejectsForgedContextWithoutACompletedOwnedBaSession() {
        Fixture fixture = new Fixture();
        var request = fixture.request();
        request.setCurrentSrsContent("Forged project supplied by HTTP client");
        org.junit.jupiter.api.Assertions.assertThrows(
                com.sam.be.common.exception.ApiException.class,
                () -> fixture.service.chatWithAiRisk(request));
        verify(fixture.geminiClient, never()).generateContent(anyString(), anyList());
    }

    @Test
    void legacyRiskUsesStoredProjectAndIgnoresClientSuppliedSrsAndHistory() throws Exception {
        Fixture fixture = new Fixture();
        String ownerKey = "ai:ba:session:00000000-0000-0000-0000-000000000001:test-session";
        var history =
                java.util.List.of(
                        java.util.Map.of(
                                "role",
                                "user",
                                "parts",
                                java.util.List.of(java.util.Map.of("text", "Dự án đặt lịch khám"))),
                        java.util.Map.of(
                                "role",
                                "model",
                                "parts",
                                java.util.List.of(
                                        java.util.Map.of(
                                                "text",
                                                "{\"status\":\"COMPLETED\",\"srsContent\":\"STORED PROJECT SRS\"}"))));
        when(fixture.values.get(ownerKey)).thenReturn(fixture.mapper.writeValueAsString(history));
        when(fixture.geminiClient.generateContent(anyString(), anyList()))
                .thenReturn(
                        "{\"status\":\"NEGOTIATING\",\"aiMessage\":\"Đang đánh giá dự án\",\"srsContent\":null}");
        ReflectionTestUtils.setField(
                fixture.service, "riskSystemPrompt", "Review only current project");
        var request = fixture.request();
        request.setCurrentSrsContent("FORGED SRS");
        request.setChatHistory(
                java.util.List.of(java.util.Map.of("role", "user", "content", "FORGED HISTORY")));
        assertEquals("NEGOTIATING", fixture.service.chatWithAiRisk(request).getStatus());
        assertEquals("STORED PROJECT SRS", request.getCurrentSrsContent());
        org.mockito.ArgumentCaptor<java.util.List<java.util.Map<String, Object>>> prompt =
                org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(fixture.geminiClient).generateContent(anyString(), prompt.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                prompt.getValue().toString().contains("STORED PROJECT SRS"));
        assertFalse(prompt.getValue().toString().contains("FORGED SRS"));
        assertFalse(prompt.getValue().toString().contains("FORGED HISTORY"));
    }

    @Test
    void contractReviewAcceptsDocumentWhenOnlyPlatformManagedIssuesRemain() {
        Fixture fixture = new Fixture();
        when(fixture.geminiClient.generateContent(anyString(), anyList()))
                .thenReturn(
                        """
                        {"extractedAmount":12000000,"textMatchesField":true,"changedFromInitial":false,
                         "issues":["Hợp đồng thiếu chữ ký của bên A và bên B","Thiếu thông tin định danh bên B"],
                         "verdict":"NEEDS_CONFIRM","chatMessage":"Cần bổ sung chữ ký"}
                        """);
        ReflectionTestUtils.setField(fixture.service, "amountReviewPrompt", "Review contract");

        var review =
                fixture.service.reviewContractTerms(
                        "Hợp đồng ...", new java.math.BigDecimal("12000000"),
                        new java.math.BigDecimal("12000000"));

        assertEquals("OK", review.getVerdict());
        assertEquals(java.util.List.of(), review.getIssues());
        assertEquals(
                "AI đã thẩm định xong: hợp đồng hợp lệ, số tiền khớp. Hai bên nạp tiền để dự án bắt đầu nhé!",
                review.getChatMessage());
    }

    @Test
    void contractReviewStillNeedsConfirmWhenAmountDiffers() {
        Fixture fixture = new Fixture();
        when(fixture.geminiClient.generateContent(anyString(), anyList()))
                .thenReturn(
                        """
                        {"extractedAmount":10000000,"textMatchesField":true,"changedFromInitial":false,
                         "issues":[],"verdict":"OK","chatMessage":null}
                        """);
        ReflectionTestUtils.setField(fixture.service, "amountReviewPrompt", "Review contract");

        var review =
                fixture.service.reviewContractTerms(
                        "Hợp đồng ...", new java.math.BigDecimal("12000000"),
                        new java.math.BigDecimal("12000000"));

        assertEquals("NEEDS_CONFIRM", review.getVerdict());
    }

    @Test
    void contractReviewStillNeedsConfirmOnRealCommercialIssue() {
        Fixture fixture = new Fixture();
        when(fixture.geminiClient.generateContent(anyString(), anyList()))
                .thenReturn(
                        """
                        {"extractedAmount":12000000,"textMatchesField":true,"changedFromInitial":false,
                         "issues":["Điều khoản bảo hành không nêu thời hạn cụ thể"],
                         "verdict":"OK","chatMessage":null}
                        """);
        ReflectionTestUtils.setField(fixture.service, "amountReviewPrompt", "Review contract");

        var review =
                fixture.service.reviewContractTerms(
                        "Hợp đồng ...", new java.math.BigDecimal("12000000"),
                        new java.math.BigDecimal("12000000"));

        assertEquals("NEEDS_CONFIRM", review.getVerdict());
        assertEquals(
                java.util.List.of("Điều khoản bảo hành không nêu thời hạn cụ thể"),
                review.getIssues());
    }

    @Test
    void contractReviewNeedsConfirmWhenAmountChangedFromAiSuggestion() {
        Fixture fixture = new Fixture();
        when(fixture.geminiClient.generateContent(anyString(), anyList()))
                .thenReturn(
                        """
                        {"extractedAmount":12000000,"textMatchesField":true,"changedFromInitial":true,
                         "issues":[],"verdict":"OK","chatMessage":null}
                        """);
        ReflectionTestUtils.setField(fixture.service, "amountReviewPrompt", "Review contract");

        var review =
                fixture.service.reviewContractTerms(
                        "Hợp đồng ...", new java.math.BigDecimal("12000000"),
                        new java.math.BigDecimal("10000000"));

        assertEquals("NEEDS_CONFIRM", review.getVerdict());
    }

    private static class Fixture {
        private final ObjectMapper mapper = new ObjectMapper();
        private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

        @SuppressWarnings("unchecked")
        private final ValueOperations<String, String> values = mock(ValueOperations.class);

        private final GeminiClient geminiClient = mock(GeminiClient.class);
        private final StorageService storageService = mock(StorageService.class);
        private final AiServiceImpl service =
                new AiServiceImpl(
                        mock(com.sam.be.modules.ai.service.ProjectPromptGuard.class),
                        mapper,
                        redis,
                        geminiClient,
                        storageService);

        private Fixture() {
            when(redis.opsForValue()).thenReturn(values);
            ReflectionTestUtils.setField(service, "baSystemPrompt", "Ask follow-up questions");
        }

        private AiChatRequest request() {
            return AiChatRequest.builder()
                    .sessionId("test-session")
                    .userMessage("Tôi cần website đặt lịch khám")
                    .build();
        }
    }
}
