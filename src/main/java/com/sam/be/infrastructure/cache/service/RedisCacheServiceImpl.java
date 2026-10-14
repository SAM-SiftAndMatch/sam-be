package com.sam.be.infrastructure.cache.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.response.PageResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheServiceImpl implements RedisCacheService {

    private static final int MAX_CACHE_VALUE_BYTES = 512 * 1024;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Override
    public <T> Optional<T> get(String key, Class<T> clazz) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                count(key, "get", "miss");
                return Optional.empty();
            }
            T value = objectMapper.readValue(json, clazz);
            count(key, "get", "hit");
            return Optional.ofNullable(value);
        } catch (Exception ex) {
            count(key, "get", "error");
            log.warn("Redis error reading key {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<List<T>> getList(String key, Class<T> elementClass) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                count(key, "getList", "miss");
                return Optional.empty();
            }
            JavaType type =
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass);
            List<T> value = objectMapper.readValue(json, type);
            count(key, "getList", "hit");
            return Optional.ofNullable(value);
        } catch (Exception ex) {
            count(key, "getList", "error");
            log.warn("Redis error reading list key {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    private <T> Optional<PageResponse<T>> getPage(String key, Class<T> elementClass) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                count(key, "getPage", "miss");
                return Optional.empty();
            }
            JavaType type =
                    objectMapper
                            .getTypeFactory()
                            .constructParametricType(PageResponse.class, elementClass);
            PageResponse<T> value = objectMapper.readValue(json, type);
            count(key, "getPage", "hit");
            return Optional.ofNullable(value);
        } catch (Exception ex) {
            count(key, "getPage", "error");
            log.warn("Redis error reading page key {}: {}", key, ex.getMessage());
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
            int valueBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (valueBytes > MAX_CACHE_VALUE_BYTES) {
                log.warn("Skipping oversized cache value for key {}", key);
                count(key, "set", "oversized");
                return;
            }
            meterRegistry
                    .summary("sam.cache.value.bytes", "namespace", namespace(key))
                    .record(valueBytes);
            if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
                long millis = ttl.toMillis();
                long jitter = Math.min(millis / 10, 30_000);
                long effectiveMillis =
                        jitter > 0
                                ? millis - ThreadLocalRandom.current().nextLong(jitter + 1)
                                : millis;
                redisTemplate.opsForValue().set(key, json, Duration.ofMillis(effectiveMillis));
            } else {
                redisTemplate.opsForValue().set(key, json);
            }
            count(key, "set", "success");
        } catch (Exception ex) {
            count(key, "set", "error");
            log.warn("Redis error writing key {}: {}", key, ex.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            count(key, "delete", "success");
        } catch (Exception ex) {
            count(key, "delete", "error");
            log.warn("Redis error deleting key {}: {}", key, ex.getMessage());
        }
    }

    @Override
    public void deleteByPattern(String pattern) {
        try {
            try (Cursor<String> cursor =
                    redisTemplate.scan(
                            ScanOptions.scanOptions().match(pattern).count(100).build())) {
                List<String> keys = new ArrayList<>(100);
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                    if (keys.size() == 100) {
                        redisTemplate.delete(keys);
                        keys.clear();
                    }
                }
                if (!keys.isEmpty()) redisTemplate.delete(keys);
            }
            count(pattern, "deletePattern", "success");
        } catch (Exception ex) {
            count(pattern, "deletePattern", "error");
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
        long start = System.nanoTime();
        T result;
        try {
            result = dbSupplier.get();
        } finally {
            recordLoadTime(key, start);
        }
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
        long start = System.nanoTime();
        List<T> result;
        try {
            result = dbSupplier.get();
        } finally {
            recordLoadTime(key, start);
        }
        if (result != null) {
            set(key, result, ttl);
        }
        return result;
    }

    @Override
    public <T> PageResponse<T> getPageOrSet(
            String key, Duration ttl, Class<T> elementClass, Supplier<PageResponse<T>> dbSupplier) {
        Optional<PageResponse<T>> cached = getPage(key, elementClass);
        if (cached.isPresent()) return cached.get();
        long start = System.nanoTime();
        PageResponse<T> result;
        try {
            result = dbSupplier.get();
        } finally {
            recordLoadTime(key, start);
        }
        if (result != null) set(key, result, ttl);
        return result;
    }

    private void count(String key, String operation, String result) {
        meterRegistry
                .counter(
                        "sam.cache.operations",
                        "namespace",
                        namespace(key),
                        "operation",
                        operation,
                        "result",
                        result)
                .increment();
    }

    private void recordLoadTime(String key, long start) {
        meterRegistry
                .timer("sam.cache.source.load", "namespace", namespace(key))
                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    }

    private String namespace(String key) {
        int separator = key.indexOf(':');
        return separator > 0 ? key.substring(0, separator) : key;
    }
}
