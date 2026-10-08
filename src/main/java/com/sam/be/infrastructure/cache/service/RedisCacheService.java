package com.sam.be.infrastructure.cache.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface RedisCacheService {

    /** Lấy dữ liệu từ cache theo key. */
    <T> Optional<T> get(String key, Class<T> clazz);

    /** Lấy danh sách đối tượng từ cache theo key. */
    <T> Optional<List<T>> getList(String key, Class<T> elementClass);

    /** Lưu đối tượng vào cache kèm thời gian sống TTL. */
    void set(String key, Object value, Duration ttl);

    /** Xóa 1 key khỏi cache. */
    void delete(String key);

    /** Xóa các key khớp với pattern (ví dụ: "skill:*"). */
    void deleteByPattern(String pattern);

    /** Kiểm tra sự tồn tại của key trong cache. */
    boolean hasKey(String key);

    /**
     * Cache-Aside Pattern: Trả về dữ liệu từ cache nếu có; nếu miss thì gọi dbSupplier, lưu cache
     * và trả về.
     */
    <T> T getOrSet(String key, Duration ttl, Class<T> clazz, Supplier<T> dbSupplier);

    /** Cache-Aside Pattern cho danh sách List. */
    <T> List<T> getListOrSet(
            String key, Duration ttl, Class<T> elementClass, Supplier<List<T>> dbSupplier);
}
