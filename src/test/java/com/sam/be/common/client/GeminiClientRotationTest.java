package com.sam.be.common.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.modules.ai.service.AiCatalogService;
import com.sam.be.modules.ai.service.AiHealthTracker;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiClientRotationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private GeminiClient newClient(
            MockRestServiceServer[] serverHolder,
            List<AiCatalogService.KeyCandidate> keys,
            List<String> models) {
        RestClient.Builder builder = RestClient.builder();
        serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
        AiCatalogService catalog = mock(AiCatalogService.class);
        when(catalog.activeKeys()).thenReturn(keys);
        when(catalog.activeModels()).thenReturn(models);
        return new GeminiClient(
                builder.build(), mapper, catalog, new AiHealthTracker(), 0L, 10_000L);
    }

    private static String okBody(String jsonPayload) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + "\""
                + jsonPayload.replace("\"", "\\\"")
                + "\""
                + "}]}}]}";
    }

    @Test
    void switchesKeyImmediatelyOn401WithoutTryingOtherModelsOnDeadKey() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        var keys =
                List.of(
                        new AiCatalogService.KeyCandidate(10, "fp1", "bad-key"),
                        new AiCatalogService.KeyCandidate(20, "fp2", "good-key"));
        var models = List.of("gemini-2.5-pro", "gemini-2.5-flash");
        GeminiClient client = newClient(holder, keys, models);
        MockRestServiceServer server = holder[0];

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "bad-key"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "good-key"))
                .andRespond(withSuccess(okBody("{\"ok\":true}"), MediaType.APPLICATION_JSON));

        String result = client.generateContent("sys", List.of(Map.of("role", "user")));
        assertEquals("{\"ok\":true}", result);
        server.verify();
    }

    @Test
    void skips404ModelAcrossRemainingKeys() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        var keys = List.of(new AiCatalogService.KeyCandidate(10, "fp1", "key-1"));
        var models = List.of("missing-model", "gemini-2.5-flash");
        GeminiClient client = newClient(holder, keys, models);
        MockRestServiceServer server = holder[0];

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/missing-model:generateContent"))
                .andExpect(header("x-goog-api-key", "key-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(header("x-goog-api-key", "key-1"))
                .andRespond(
                        withSuccess(okBody("{\"model\":\"flash\"}"), MediaType.APPLICATION_JSON));

        String result = client.generateContent("sys", List.of(Map.of("role", "user")));
        assertEquals("{\"model\":\"flash\"}", result);
        server.verify();
    }

    @Test
    void rotatesModelsFirstOn429ThenSwitchesKeyWhenAllModelsExhausted() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        var keys =
                List.of(
                        new AiCatalogService.KeyCandidate(10, "fp1", "key-1"),
                        new AiCatalogService.KeyCandidate(20, "fp2", "key-2"));
        var models = List.of("gemini-2.5-pro", "gemini-2.5-flash");
        GeminiClient client = newClient(holder, keys, models);
        MockRestServiceServer server = holder[0];

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent"))
                .andExpect(header("x-goog-api-key", "key-1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(header("x-goog-api-key", "key-1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent"))
                .andExpect(header("x-goog-api-key", "key-2"))
                .andRespond(withSuccess(okBody("{\"key\":2}"), MediaType.APPLICATION_JSON));

        String result = client.generateContent("sys", List.of(Map.of("role", "user")));
        assertEquals("{\"key\":2}", result);
        server.verify();
    }

    @Test
    void retriesOverloaded503TwiceBeforeSwitchingToNextModel() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        var keys = List.of(new AiCatalogService.KeyCandidate(10, "fp1", "key-1"));
        var models = List.of("gemini-2.5-pro", "gemini-2.5-flash");
        GeminiClient client = newClient(holder, keys, models);
        MockRestServiceServer server = holder[0];

        // 1 initial + 2 retries = 3 attempts on gemini-2.5-pro
        for (int i = 0; i < 3; i++) {
            server.expect(
                            requestTo(
                                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent"))
                    .andExpect(header("x-goog-api-key", "key-1"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(header("x-goog-api-key", "key-1"))
                .andRespond(withSuccess(okBody("{\"fallback\":true}"), MediaType.APPLICATION_JSON));

        String result = client.generateContent("sys", List.of(Map.of("role", "user")));
        assertEquals("{\"fallback\":true}", result);
        server.verify();
    }

    @Test
    void testConnectionReturnsDiagnosticForSuccessAndFailure() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        GeminiClient client = newClient(holder, List.of(), List.of());
        MockRestServiceServer server = holder[0];

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(header("x-goog-api-key", "raw-key"))
                .andRespond(withSuccess(okBody("{\"ok\":true}"), MediaType.APPLICATION_JSON));

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/bad-model:generateContent"))
                .andExpect(header("x-goog-api-key", "raw-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        var ok = client.testConnection("raw-key", "gemini-2.5-flash");
        assertTrue(ok.ok());
        assertEquals(200, ok.statusCode());

        var fail = client.testConnection("raw-key", "bad-model");
        assertFalse(fail.ok());
        assertEquals(404, fail.statusCode());
        server.verify();
    }

    @Test
    void skipsThoughtPartAndPicksFinalJsonOutput() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        var keys = List.of(new AiCatalogService.KeyCandidate(10, "fp1", "key-1"));
        var models = List.of("gemma-4-31b-it");
        GeminiClient client = newClient(holder, keys, models);
        MockRestServiceServer server = holder[0];

        String thinkingResponse =
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"thinking {ignore}\",\"thought\":true},{\"text\":\"{\\\"answer\\\":42}\"}]}}]}";

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemma-4-31b-it:generateContent"))
                .andExpect(header("x-goog-api-key", "key-1"))
                .andRespond(withSuccess(thinkingResponse, MediaType.APPLICATION_JSON));

        String result = client.generateContent("sys", List.of(Map.of("role", "user")));
        assertEquals("{\"answer\":42}", result);
        server.verify();
    }

    @Test
    void testConnectionDistinguishesOverloadedTimeoutQuotaAndUnknownErrors() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        GeminiClient client = newClient(holder, List.of(), List.of());
        MockRestServiceServer server = holder[0];

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/m-503:generateContent"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/m-429:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/m-timeout:generateContent"))
                .andRespond(
                        req -> {
                            throw new org.springframework.web.client.ResourceAccessException(
                                    "I/O error", new java.net.SocketTimeoutException("Read timed out"));
                        });
        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/m-unknown:generateContent"))
                .andRespond(
                        req -> {
                            throw new IllegalStateException("Weird unexpected parser bug");
                        });

        var r503 = client.testConnection("k", "m-503");
        assertTrue(r503.message().contains("quá tải"));

        var r429 = client.testConnection("k", "m-429");
        assertTrue(r429.message().contains("Hết lượt sử dụng"));

        var rTimeout = client.testConnection("k", "m-timeout");
        assertTrue(rTimeout.message().contains("Timeout kết nối"));

        var rUnknown = client.testConnection("k", "m-unknown");
        assertTrue(rUnknown.message().contains("Lỗi không xác định"));

        server.verify();
    }

    @Test
    void testKeyConnectionTreats503OverloadAsValidKeyWithWarning() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        GeminiClient client = newClient(holder, List.of(), List.of());
        MockRestServiceServer server = holder[0];

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        var res = client.testKeyConnection("valid-key", List.of("gemini-3.5-flash-lite"));
        assertTrue(res.ok());
        assertEquals(503, res.statusCode());
        assertTrue(res.message().contains("Key hợp lệ"));
        server.verify();
    }

    @Test
    void timeoutSwitchesModelImmediatelyWithoutBurningBudgetOnRetries() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        var keys = List.of(new AiCatalogService.KeyCandidate(10, "fp1", "key-1"));
        var models = List.of("slow-model", "fast-model");
        GeminiClient client = newClient(holder, keys, models);
        MockRestServiceServer server = holder[0];

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/slow-model:generateContent"))
                .andRespond(
                        req -> {
                            throw new org.springframework.web.client.ResourceAccessException(
                                    "I/O error", new java.net.SocketTimeoutException("Read timed out"));
                        });

        server.expect(
                        requestTo(
                                "https://generativelanguage.googleapis.com/v1beta/models/fast-model:generateContent"))
                .andRespond(withSuccess(okBody("{\"fast\":true}"), MediaType.APPLICATION_JSON));

        String result = client.generateContent("sys", List.of(Map.of("role", "user")));
        assertEquals("{\"fast\":true}", result);
        server.verify();
    }
}
