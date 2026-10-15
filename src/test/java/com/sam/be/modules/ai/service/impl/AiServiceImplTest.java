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
                .set(eq("ai:ba:session:test-session"), saved.capture(), eq(Duration.ofHours(24)));
        JsonNode history = fixture.mapper.readTree(saved.getValue());
        String modelReply = history.get(1).get("parts").get(0).get("text").asText();
        assertEquals("ASKING", fixture.mapper.readTree(modelReply).get("status").asText());
    }

    @Test
    void laterTurnCanCompleteSrsAfterQuestions() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.values.get("ai:ba:session:test-session"))
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

    private static class Fixture {
        private final ObjectMapper mapper = new ObjectMapper();
        private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

        @SuppressWarnings("unchecked")
        private final ValueOperations<String, String> values = mock(ValueOperations.class);

        private final GeminiClient geminiClient = mock(GeminiClient.class);
        private final StorageService storageService = mock(StorageService.class);
        private final AiServiceImpl service =
                new AiServiceImpl(mapper, redis, geminiClient, storageService);

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
