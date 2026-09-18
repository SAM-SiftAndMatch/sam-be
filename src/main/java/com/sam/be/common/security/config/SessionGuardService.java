package com.sam.be.common.security.config;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.infrastructure.cache.service.SessionCacheService;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

/**
 * Kiểm tra trạng thái hợp lệ của session (active vs revoked) theo mô hình 3 tầng: - Tier 1: Redis
 * Blacklist (O(1) fast fail) - Tier 2: Redis Whitelist (O(1) fast pass) - Tier 3: Database Source
 * of Truth (Fallback)
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SessionGuardService {

    SessionCacheService sessionCacheService;
    Optional<SessionValidator> sessionValidator;

    static final Duration ACTIVE_TTL = Duration.ofSeconds(60);

    public void ensureActive(UUID sessionId, Duration jwtTtl) {
        if (sessionId == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        if (jwtTtl == null || jwtTtl.isNegative() || jwtTtl.isZero()) {
            jwtTtl = Duration.ofSeconds(1);
        }

        // Tier 1: Check the Revoked Blacklist in Redis (O(1) fast fail)
        try {
            if (sessionCacheService.isRevoked(sessionId)) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception ignored) {
        }

        // Tier 2: Check the Active Whitelist in Redis
        try {
            if (sessionCacheService.isActive(sessionId)) {
                return; // Session is healthy, allow pass
            }
        } catch (Exception ignored) {
        }

        // Tier 3: Cache Miss or Redis Down -> Fallback to Source of Truth (DB)
        boolean active = sessionValidator.map(v -> v.isSessionActive(sessionId)).orElse(true);
        if (!active) {
            // DB says dead -> Đồng bộ sang Redis để các request sau không hit DB
            try {
                sessionCacheService.markRevoked(sessionId, jwtTtl);
                sessionCacheService.clearActive(sessionId);
                sessionCacheService.clearAuthz(sessionId);
            } catch (Exception ignored) {
            }
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }

        // DB says alive -> Cache lại trạng thái Active trong 60s
        try {
            sessionCacheService.markActive(sessionId, ACTIVE_TTL);
        } catch (Exception ignored) {
        }
    }
}
