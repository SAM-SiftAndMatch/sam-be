package com.sam.be.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.ai.service.AiCatalogService;
import com.sam.be.modules.ai.service.AiHealthTracker;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class GeminiClient {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int OVERLOAD_MAX_RETRIES = 2; // 1 initial attempt + 2 retries = 3 total
    private static final long DEFAULT_RETRY_DELAY_MS = 1500L;
    private static final long DEFAULT_BUDGET_MS = 90_000L;
    private static final long SCOPE_BUDGET_MS = 20_000L;

    public record TestOutcome(
            boolean ok, Integer statusCode, long latencyMs, String modelUsed, String message) {}

    private enum FailureKind {
        KEY_INVALID,
        MODEL_INVALID,
        QUOTA,
        OVERLOADED,
        SERVER_ERROR,
        TIMEOUT,
        NETWORK,
        EMPTY_OUTPUT,
        BAD_REQUEST,
        UNKNOWN
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiCatalogService catalogService;
    private final AiHealthTracker healthTracker;
    private final long retryDelayMs;
    private final long defaultBudgetMs;
    private final AtomicInteger keyCursor = new AtomicInteger(0);

    @Autowired
    public GeminiClient(
            ObjectMapper objectMapper,
            AiCatalogService catalogService,
            AiHealthTracker healthTracker) {
        this(
                buildTimeoutRestClient(),
                objectMapper,
                catalogService,
                healthTracker,
                DEFAULT_RETRY_DELAY_MS,
                DEFAULT_BUDGET_MS);
    }

    GeminiClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            AiCatalogService catalogService,
            AiHealthTracker healthTracker,
            long retryDelayMs,
            long defaultBudgetMs) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.catalogService = catalogService;
        this.healthTracker = healthTracker;
        this.retryDelayMs = retryDelayMs;
        this.defaultBudgetMs = defaultBudgetMs;
    }

    private static RestClient buildTimeoutRestClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(18));
        return RestClient.builder().requestFactory(factory).build();
    }

    public String generateContent(String systemPrompt, List<Map<String, Object>> history) {
        return executeWithSmartRotation(systemPrompt, history, 8192, true, defaultBudgetMs);
    }

    /** Cheap scope classification: small output budget, no prolonged retry loops. */
    public String classifyProjectScope(String systemPrompt, String input) {
        return executeWithSmartRotation(
                systemPrompt,
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", input)))),
                512,
                false,
                Math.min(defaultBudgetMs, SCOPE_BUDGET_MS));
    }

    public TestOutcome testConnection(String rawKey, String modelName) {
        long start = System.nanoTime();
        try {
            String text =
                    executeSingleRequest(
                            modelName,
                            rawKey,
                            "You are a health check responder. Reply with {\"ok\":true}.",
                            List.of(
                                    Map.of(
                                            "role",
                                            "user",
                                            "parts",
                                            List.of(Map.of("text", "ping")))),
                            512);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (text == null || text.isBlank()) {
                return new TestOutcome(
                        false,
                        200,
                        elapsedMs,
                        modelName,
                        "Gemini phản hồi 200 nhưng không có nội dung (bị safety filter hoặc hết token)");
            }
            return new TestOutcome(
                    true,
                    200,
                    elapsedMs,
                    modelName,
                    "Kết nối Gemini thành công (" + modelName + ")");
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            Integer status = extractHttpStatus(e);
            FailureKind kind = classifyFailure(e);
            String detail = diagnoseMessage(kind, status, modelName, e);
            return new TestOutcome(false, status, elapsedMs, modelName, detail);
        }
    }

    /**
     * Test riêng cho API key: thử theo danh sách model nhẹ (ưu tiên flash-lite). Nếu Google trả 503
     * (model quá tải) tức là key đã qua bước xác thực hợp lệ, nên coi key hợp lệ kèm cảnh báo quá
     * tải.
     */
    public TestOutcome testKeyConnection(String rawKey, List<String> candidateModels) {
        if (candidateModels == null || candidateModels.isEmpty()) {
            return testConnection(rawKey, "gemini-3.5-flash-lite");
        }
        TestOutcome last = null;
        for (String model : candidateModels) {
            TestOutcome outcome = testConnection(rawKey, model);
            if (outcome.ok()) {
                return outcome;
            }
            last = outcome;
            if (outcome.statusCode() != null
                    && (outcome.statusCode() == 401 || outcome.statusCode() == 403)) {
                return outcome;
            }
            if (outcome.statusCode() == null
                    || (outcome.statusCode() != 503 && outcome.statusCode() != 404)) {
                return outcome;
            }
        }
        if (last != null && Integer.valueOf(503).equals(last.statusCode())) {
            return new TestOutcome(
                    true,
                    503,
                    last.latencyMs(),
                    last.modelUsed(),
                    "Key hợp lệ (Google đã xác thực), nhưng model '"
                            + last.modelUsed()
                            + "' đang quá tải (HTTP 503)");
        }
        return last;
    }

    private String executeWithSmartRotation(
            String systemPrompt,
            List<Map<String, Object>> history,
            int maxOutputTokens,
            boolean allowOverloadRetries,
            long budgetMs) {
        List<AiCatalogService.KeyCandidate> keys =
                healthTracker.orderKeys(catalogService.activeKeys(), keyCursor);
        List<String> models = catalogService.activeModels();

        if (keys.isEmpty() || models.isEmpty()) {
            log.error("Không có Gemini API key hoặc model nào khả dụng để gọi AI.");
            throw new ApiException(ErrorCode.UNEXPECTED_ERROR);
        }

        long deadlineMs = System.currentTimeMillis() + Math.max(1000L, budgetMs);
        Set<String> invalidModelsInRequest = new HashSet<>();
        int maxAttemptsPerPair = allowOverloadRetries ? (1 + OVERLOAD_MAX_RETRIES) : 1;
        Exception lastException = null;

        for (AiCatalogService.KeyCandidate key : keys) {
            String keyTag = shortFp(key.fingerprint());
            List<String> modelsForKey = healthTracker.orderModelsForKey(models, key.fingerprint());
            int usableModels = 0;
            int quotaFailuresForKey = 0;

            modelLoop:
            for (String model : modelsForKey) {
                if (invalidModelsInRequest.contains(model)) {
                    continue;
                }
                usableModels++;

                for (int attempt = 1; attempt <= maxAttemptsPerPair; attempt++) {
                    if (System.currentTimeMillis() >= deadlineMs) {
                        log.error("Hết budget {}ms khi xoay Gemini key/model.", budgetMs);
                        throw new ApiException(ErrorCode.UNEXPECTED_ERROR);
                    }
                    try {
                        String raw =
                                executeSingleRequest(
                                        model,
                                        key.rawKey(),
                                        systemPrompt,
                                        history,
                                        maxOutputTokens);
                        keyCursor.incrementAndGet();
                        healthTracker.markPairSuccess(key.fingerprint(), model);
                        return extractValidJson(raw);
                    } catch (Exception e) {
                        lastException = e;
                        FailureKind kind = classifyFailure(e);
                        Integer status = extractHttpStatus(e);
                        log.warn(
                                "Gemini call failed (key={}, model={}, attempt={}/{}, kind={}, status={}): {}",
                                keyTag,
                                model,
                                attempt,
                                maxAttemptsPerPair,
                                kind,
                                status,
                                safeErrorSummary(e));

                        switch (kind) {
                            case KEY_INVALID -> {
                                healthTracker.markKeyDead(key.fingerprint());
                                log.warn(
                                        "Key {} không hợp lệ/bị chặn, đổi sang key tiếp theo.",
                                        keyTag);
                                break modelLoop;
                            }
                            case MODEL_INVALID -> {
                                invalidModelsInRequest.add(model);
                                usableModels = Math.max(0, usableModels - 1);
                                log.warn(
                                        "Model {} không tồn tại (404), đổi sang model tiếp theo.",
                                        model);
                                continue modelLoop;
                            }
                            case QUOTA -> {
                                healthTracker.markPairQuota(key.fingerprint(), model);
                                quotaFailuresForKey++;
                                log.warn(
                                        "Cặp ({}, {}) hết hạn mức (429), đổi sang model tiếp theo.",
                                        keyTag,
                                        model);
                                continue modelLoop;
                            }
                            case BAD_REQUEST, EMPTY_OUTPUT, UNKNOWN -> {
                                continue modelLoop;
                            }
                            case TIMEOUT -> {
                                // Đã chờ hết readTimeout -> đổi ngay sang model tiếp theo để không
                                // đốt cạn budget
                                log.warn(
                                        "Model {} bị timeout kết nối, chuyển ngay sang model tiếp theo.",
                                        model);
                                continue modelLoop;
                            }
                            case OVERLOADED, SERVER_ERROR, NETWORK -> {
                                if (attempt >= maxAttemptsPerPair) {
                                    log.warn(
                                            "Model {} lỗi {} sau {} lần thử, đổi model.",
                                            model,
                                            kind,
                                            attempt);
                                    continue modelLoop;
                                }
                                if (!sleepWithinBudget(deadlineMs, retryDelayMs)) {
                                    throw new ApiException(ErrorCode.UNEXPECTED_ERROR);
                                }
                            }
                        }
                    }
                }
            }

            if (usableModels > 0 && quotaFailuresForKey >= usableModels) {
                healthTracker.markKeyQuotaExhausted(key.fingerprint());
            }
            keyCursor.incrementAndGet();
        }

        log.error("Exhausted all Gemini keys and models.", lastException);
        throw new ApiException(ErrorCode.UNEXPECTED_ERROR);
    }

    private String executeSingleRequest(
            String model,
            String apiKey,
            String systemPrompt,
            List<Map<String, Object>> history,
            int maxOutputTokens) {
        String url = BASE_URL + model + ":generateContent";

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("maxOutputTokens", maxOutputTokens);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        requestBody.put("contents", history);
        requestBody.put("generationConfig", generationConfig);

        String responseStr =
                restClient
                        .post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-goog-api-key", apiKey)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

        try {
            JsonNode rootNode = objectMapper.readTree(responseStr);
            JsonNode partsNode = rootNode.path("candidates").path(0).path("content").path("parts");
            String resolvedText = extractCandidateText(partsNode);

            if (resolvedText == null || resolvedText.isBlank()) {
                throw new EmptyCandidateException();
            }
            return resolvedText;
        } catch (EmptyCandidateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Error parsing Gemini API structure", e);
        }
    }

    private static final class EmptyCandidateException extends RuntimeException {
        EmptyCandidateException() {
            super("Empty response from Gemini API");
        }
    }

    private static String extractCandidateText(JsonNode partsNode) {
        if (!partsNode.isArray() || partsNode.isEmpty()) {
            return null;
        }
        String fallbackText = null;
        for (JsonNode part : partsNode) {
            String text = part.path("text").asText(null);
            if (text == null || text.isBlank()) {
                continue;
            }
            if (!part.path("thought").asBoolean(false)) {
                return text;
            }
            fallbackText = text;
        }
        return fallbackText;
    }

    private FailureKind classifyFailure(Throwable e) {
        if (e instanceof EmptyCandidateException) {
            return FailureKind.EMPTY_OUTPUT;
        }
        if (hasCause(e, SocketTimeoutException.class) || hasCause(e, HttpTimeoutException.class)) {
            return FailureKind.TIMEOUT;
        }
        if (hasCause(e, UnknownHostException.class)
                || hasCause(e, ConnectException.class)
                || hasCause(e, SSLException.class)) {
            return FailureKind.NETWORK;
        }

        Integer status = extractHttpStatus(e);
        if (status != null) {
            if (status == 401 || status == 403) return FailureKind.KEY_INVALID;
            if (status == 404) return FailureKind.MODEL_INVALID;
            if (status == 429) return FailureKind.QUOTA;
            if (status == 503) return FailureKind.OVERLOADED;
            if (status == 504) return FailureKind.TIMEOUT;
            if (status >= 500) return FailureKind.SERVER_ERROR;
            if (status == 400 || status == 422) {
                String bodyUpper = safeBodyUpper(e);
                if (bodyUpper.contains("API_KEY_INVALID")
                        || bodyUpper.contains("API KEY NOT VALID")) {
                    return FailureKind.KEY_INVALID;
                }
                if (bodyUpper.contains("MODEL_NOT_FOUND")) {
                    return FailureKind.MODEL_INVALID;
                }
                return FailureKind.BAD_REQUEST;
            }
        }

        String msg = safeErrorSummary(e).toUpperCase(Locale.ROOT);
        if (msg.contains("TIMED OUT") || msg.contains("TIMEOUT")) {
            return FailureKind.TIMEOUT;
        }
        if (msg.contains("API_KEY_INVALID")
                || msg.contains("API KEY NOT VALID")
                || msg.contains("PERMISSION_DENIED")
                || msg.contains("UNAUTHENTICATED")
                || hasNumericCode(msg, 401)
                || hasNumericCode(msg, 403)) {
            return FailureKind.KEY_INVALID;
        }
        if (msg.contains("MODEL_NOT_FOUND") || hasNumericCode(msg, 404)) {
            return FailureKind.MODEL_INVALID;
        }
        if (msg.contains("RESOURCE_EXHAUSTED")
                || msg.contains("QUOTA")
                || msg.contains("RATE LIMIT")
                || msg.contains("TOO MANY REQUESTS")
                || hasNumericCode(msg, 429)) {
            return FailureKind.QUOTA;
        }
        if (msg.contains("HIGH DEMAND")
                || msg.contains("OVERLOADED")
                || msg.contains("UNAVAILABLE")
                || hasNumericCode(msg, 503)) {
            return FailureKind.OVERLOADED;
        }
        if (e instanceof ResourceAccessException) {
            return FailureKind.NETWORK;
        }
        return FailureKind.UNKNOWN;
    }

    private Integer extractHttpStatus(Throwable e) {
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            if (cur instanceof HttpStatusCodeException httpEx) {
                return httpEx.getStatusCode().value();
            }
            if (cur instanceof RestClientResponseException restEx) {
                return restEx.getStatusCode().value();
            }
            if (cur.getCause() == cur) break;
            cur = cur.getCause();
        }
        return null;
    }

    private String safeBodyUpper(Throwable e) {
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            if (cur instanceof RestClientResponseException restEx) {
                String body = restEx.getResponseBodyAsString();
                return body == null ? "" : body.toUpperCase(Locale.ROOT);
            }
            if (cur.getCause() == cur) break;
            cur = cur.getCause();
        }
        return "";
    }

    private String extractUpstreamErrorMessage(Throwable e) {
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            if (cur instanceof RestClientResponseException restEx) {
                String body = restEx.getResponseBodyAsString();
                if (body != null && !body.isBlank()) {
                    try {
                        JsonNode msgNode =
                                objectMapper.readTree(body).path("error").path("message");
                        if (!msgNode.isMissingNode() && !msgNode.asText().isBlank()) {
                            return clipMsg(msgNode.asText().replaceAll("\\s+", " "), 180);
                        }
                    } catch (Exception ignored) {
                        // Fall through to raw clipped summary
                    }
                    return clipMsg(body.replaceAll("\\s+", " "), 180);
                }
            }
            if (cur.getCause() == cur) break;
            cur = cur.getCause();
        }
        return "";
    }

    private String diagnoseMessage(
            FailureKind kind, Integer status, String modelName, Throwable e) {
        String prefix = status == null ? "" : " (HTTP " + status + ")";
        String upstream = extractUpstreamErrorMessage(e);
        String suffix = upstream.isBlank() ? "" : ": " + upstream;
        return switch (kind) {
            case KEY_INVALID -> "API key không hợp lệ hoặc bị từ chối quyền" + prefix + suffix;
            case MODEL_INVALID ->
                    "Model '" + modelName + "' không tồn tại hoặc không khả dụng" + prefix;
            case QUOTA -> "Hết lượt sử dụng / vượt hạn mức (Quota)" + prefix + suffix;
            case OVERLOADED -> "Model '" + modelName + "' đang quá tải (High demand)" + prefix;
            case SERVER_ERROR -> "Máy chủ Google Gemini gặp lỗi tạm thời" + prefix;
            case TIMEOUT -> "Timeout kết nối: Gemini không phản hồi trong 18 giây" + prefix;
            case NETWORK ->
                    "Lỗi mạng: Không thể kết nối tới máy chủ Gemini ("
                            + clipMsg(safeErrorSummary(e), 140)
                            + ")";
            case EMPTY_OUTPUT -> "Gemini trả 200 nhưng rỗng nội dung (bị lọc hoặc hết token)";
            case BAD_REQUEST -> "Yêu cầu không hợp lệ" + prefix + suffix;
            case UNKNOWN ->
                    "Lỗi không xác định" + prefix + ": " + clipMsg(safeErrorSummary(e), 180);
        };
    }

    private String safeErrorSummary(Throwable e) {
        if (e instanceof RestClientResponseException restEx) {
            String body = restEx.getResponseBodyAsString();
            if (body != null && !body.isBlank()) {
                return clipMsg(body.replaceAll("\\s+", " "), 300);
            }
        }
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg == null || msg.isBlank()) ? root.getClass().getSimpleName() : clipMsg(msg, 300);
    }

    private static String clipMsg(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static boolean hasNumericCode(String upperMsg, int code) {
        return Pattern.compile("(?<!\\d)" + code + "(?!\\d)").matcher(upperMsg).find();
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            if (type.isInstance(cur)) return true;
            if (cur.getCause() == cur) break;
            cur = cur.getCause();
        }
        return false;
    }

    private boolean sleepWithinBudget(long deadlineMs, long delayMs) {
        long remaining = deadlineMs - System.currentTimeMillis();
        if (remaining <= 0) return false;
        long actual = Math.min(delayMs, remaining);
        if (actual <= 0) return true;
        try {
            Thread.sleep(actual);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String shortFp(String fp) {
        if (fp == null || fp.length() < 8) return "unknown";
        return fp.substring(0, 8);
    }

    private String extractValidJson(String rawResponse) {
        if (rawResponse == null) {
            return "";
        }
        int startIndex = rawResponse.indexOf('{');
        int endIndex = rawResponse.lastIndexOf('}');

        if (startIndex != -1 && endIndex != -1 && startIndex <= endIndex) {
            return rawResponse.substring(startIndex, endIndex + 1);
        }
        return rawResponse.trim();
    }
}
