package com.sam.be.infrastructure.cache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.model.SessionAuthzCache;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SessionCacheService {

    StringRedisTemplate redisTemplate;
    ObjectMapper objectMapper;

    public boolean isRevoked(UUID sessionId) {
        try {
            Boolean hasKey = redisTemplate.hasKey(RedisKeys.sessionRevoked(sessionId));
            return Boolean.TRUE.equals(hasKey);
        } catch (Exception e) {
            log.warn(
                    "Redis error checking isRevoked for sessionId={}: {}",
                    sessionId,
                    e.getMessage());
            return false;
        }
    }

    public boolean isActive(UUID sessionId) {
        try {
            Boolean hasKey = redisTemplate.hasKey(RedisKeys.sessionActive(sessionId));
            return Boolean.TRUE.equals(hasKey);
        } catch (Exception e) {
            log.warn(
                    "Redis error checking isActive for sessionId={}: {}",
                    sessionId,
                    e.getMessage());
            return false;
        }
    }

    public void markRevoked(UUID sessionId, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(RedisKeys.sessionRevoked(sessionId), "1", ttl);
        } catch (Exception e) {
            log.warn(
                    "Redis error setting markRevoked for sessionId={}: {}",
                    sessionId,
                    e.getMessage());
        }
    }

    public void markActive(UUID sessionId, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(RedisKeys.sessionActive(sessionId), "1", ttl);
        } catch (Exception e) {
            log.warn(
                    "Redis error setting markActive for sessionId={}: {}",
                    sessionId,
                    e.getMessage());
        }
    }

    public void clearActive(UUID sessionId) {
        try {
            redisTemplate.delete(RedisKeys.sessionActive(sessionId));
        } catch (Exception e) {
            log.warn("Redis error clearing active for sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    public void clearAuthz(UUID sessionId) {
        try {
            redisTemplate.delete(RedisKeys.sessionAuthz(sessionId));
        } catch (Exception e) {
            log.warn("Redis error clearing authz for sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    public Optional<SessionAuthzCache> getAuthz(UUID sessionId) {
        try {
            String json = redisTemplate.opsForValue().get(RedisKeys.sessionAuthz(sessionId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, SessionAuthzCache.class));
        } catch (Exception e) {
            log.warn("Redis error getting authz for sessionId={}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    public void putAuthz(UUID sessionId, SessionAuthzCache authz, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(authz);
            redisTemplate.opsForValue().set(RedisKeys.sessionAuthz(sessionId), json, ttl);
        } catch (Exception e) {
            log.warn("Redis error putting authz for sessionId={}: {}", sessionId, e.getMessage());
        }
    }
}
