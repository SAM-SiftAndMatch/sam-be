package com.sam.be.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private static final int MAX_RETRIES = 4;
    private static final long RETRY_DELAY_MS = 10000L;

    @Value("${GEMINI_API_KEY}")
    private String apiKey;

    @Value("${GEMINI_MODEL}")
    private String model;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public String generateContent(String systemPrompt, List<Map<String, Object>> history) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                String rawResponse = executeRequest(systemPrompt, history);
                return extractValidJson(rawResponse);
            } catch (Exception e) {
                attempt++;
                lastException = e;
                log.warn("Gemini API call failed (Attempt {}/{})", attempt, MAX_RETRIES, e);

                if (attempt >= MAX_RETRIES) {
                    break;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ApiException(ErrorCode.UNEXPECTED_ERROR);
                }
            }
        }
        log.error("Exhausted all retries for Gemini API.", lastException);
        throw new ApiException(ErrorCode.UNEXPECTED_ERROR);
    }

    private String executeRequest(String systemPrompt, List<Map<String, Object>> history) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("maxOutputTokens", 8192);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        requestBody.put("contents", history);
        requestBody.put("generationConfig", generationConfig);

        String responseStr = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            JsonNode rootNode = objectMapper.readTree(responseStr);
            JsonNode textNode = rootNode.path("candidates").path(0).path("content").path("parts").path(0).path("text");

            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new RuntimeException("Empty response from Gemini API");
            }
            return textNode.asText();
        } catch (Exception e) {
            throw new RuntimeException("Error parsing Gemini API structure", e);
        }
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