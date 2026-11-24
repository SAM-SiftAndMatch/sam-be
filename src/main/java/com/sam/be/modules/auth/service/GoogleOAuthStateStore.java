package com.sam.be.modules.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleOAuthStateStore {
    private static final Duration ATTEMPT_TTL = Duration.ofMinutes(5);
    private static final Duration PENDING_TTL = Duration.ofMinutes(10);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public record Attempt(
            String verifier, String nonce, String intent, String userId, boolean rememberMe) {}

    public record PendingIdentity(
            String issuer,
            String subject,
            String email,
            String name,
            boolean authoritativeEmail,
            boolean rememberMe) {}

    public record PasswordSetupGrant(UUID userId) {}

    public void savePasswordSetupGrant(String ticket, UUID userId) {
        put(
                "google:oauth:password-setup:",
                ticket,
                new PasswordSetupGrant(userId),
                Duration.ofMinutes(5));
    }

    public Optional<PasswordSetupGrant> getPasswordSetupGrant(String ticket) {
        if (ticket == null || ticket.isBlank()) return Optional.empty();
        String json = redis.opsForValue().get("google:oauth:password-setup:" + ticket);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(json, PasswordSetupGrant.class));
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    public Optional<PasswordSetupGrant> takePasswordSetupGrant(String ticket) {
        return take("google:oauth:password-setup:", ticket, PasswordSetupGrant.class);
    }

    public void saveAttempt(String state, Attempt attempt) {
        put("google:oauth:attempt:", state, attempt, ATTEMPT_TTL);
    }

    public Optional<Attempt> takeAttempt(String state) {
        return take("google:oauth:attempt:", state, Attempt.class);
    }

    public void savePending(String ticket, PendingIdentity identity) {
        put("google:oauth:pending:", ticket, identity, PENDING_TTL);
    }

    public Optional<PendingIdentity> takePending(String ticket) {
        return take("google:oauth:pending:", ticket, PendingIdentity.class);
    }

    private void put(String prefix, String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(prefix + key, mapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    private <T> Optional<T> take(String prefix, String key, Class<T> type) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String json = redis.opsForValue().getAndDelete(prefix + key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }
}
