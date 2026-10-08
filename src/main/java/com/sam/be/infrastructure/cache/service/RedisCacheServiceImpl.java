package com.sam.be.infrastructure.cache.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheServiceImpl implements RedisCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public <T> Optional<T> get(String key, Class<T> clazz) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(json, clazz));
        } catch (Exception ex) {
            log.warn("Redis error reading key {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<List<T>> getList(String key, Class<T> elementClass) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            JavaType type =
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass);
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception ex) {
            log.warn("Redis error reading list key {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        if (value == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
                redisTemplate.opsForValue().set(key, json, ttl);
            } else {
                redisTemplate.opsForValue().set(key, json);
            }
        } catch (Exception ex) {
            log.warn("Redis error writing key {}: {}", key, ex.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("Redis error deleting key {}: {}", key, ex.getMessage());
        }
    }

    @Override
    public void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ex) {
            log.warn("Redis error deleting keys with pattern {}: {}", pattern, ex.getMessage());
        }
    }

    @Override
    public boolean hasKey(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception ex) {
            log.warn("Redis error checking existence of key {}: {}", key, ex.getMessage());
            return false;
        }
    }

    @Override
    public <T> T getOrSet(String key, Duration ttl, Class<T> clazz, Supplier<T> dbSupplier) {
        Optional<T> cached = get(key, clazz);
        if (cached.isPresent()) {
            return cached.get();
        }
        T result = dbSupplier.get();
        if (result != null) {
            set(key, result, ttl);
        }
        return result;
    }

    @Override
    public <T> List<T> getListOrSet(
            String key, Duration ttl, Class<T> elementClass, Supplier<List<T>> dbSupplier) {
        Optional<List<T>> cached = getList(key, elementClass);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<T> result = dbSupplier.get();
        if (result != null) {
            set(key, result, ttl);
        }
        return result;
    }
}
