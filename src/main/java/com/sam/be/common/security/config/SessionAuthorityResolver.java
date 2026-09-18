package com.sam.be.common.security.config;

import com.sam.be.common.exception.ApiException;
import com.sam.be.infrastructure.cache.model.SessionAuthzCache;
import com.sam.be.infrastructure.cache.service.SessionCacheService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Nạp thông tin quyền hạn của người dùng theo mô hình Cache-Aside với Redis. Sử dụng chiến lược
 * Dynamic TTL: TTL của Redis được tính chính xác theo thời gian hết hạn còn lại của JWT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SessionAuthorityResolver {

    SessionCacheService sessionCacheService;
    Optional<AuthorityLoader> authorityLoader;
    SessionGuardService sessionGuardService;

    public SessionAuthzCache resolve(
            UUID sessionId, UUID userId, Instant jwtExp, String accountType) {
        // 1. Dynamic TTL Calculation: Đồng bộ TTL của Redis theo JWT Expiration
        Duration ttl = Duration.between(Instant.now(), jwtExp);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofSeconds(1);
        }

        // 2. The Guard: Kiểm tra xem session có bị thu hồi hoặc đã hết hạn chưa
        sessionGuardService.ensureActive(sessionId, ttl);

        try {
            // 3. Cache HIT: Lấy trực tiếp từ Redis (O(1))
            SessionAuthzCache cached = sessionCacheService.getAuthz(sessionId).orElse(null);
            if (cached != null) {
                return cached;
            }

            // 4. Cache MISS: Truy vấn Database qua AuthorityLoader, lưu vào Redis và trả về
            SessionAuthzCache loaded =
                    authorityLoader
                            .map(loader -> loader.load(userId))
                            .orElseGet(() -> defaultAuthz(userId, accountType));

            sessionCacheService.putAuthz(sessionId, loaded, ttl);
            return loaded;

        } catch (ApiException e) {
            throw e;
        } catch (Exception ex) {
            // 5. Fault Tolerance: Nếu Redis lỗi, fallback truy vấn thẳng DB
            log.warn(
                    "AUTHZ cache FAIL -> fallback DB/default sessionId={} userId={}: {}",
                    sessionId,
                    userId,
                    ex.getMessage());
            return authorityLoader
                    .map(loader -> loader.load(userId))
                    .orElseGet(() -> defaultAuthz(userId, accountType));
        }
    }

    private SessionAuthzCache defaultAuthz(UUID userId, String accountType) {
        String role = (accountType != null && !accountType.isBlank()) ? accountType : "FREELANCER";
        return new SessionAuthzCache(userId, role, List.of(role), List.of());
    }
}
