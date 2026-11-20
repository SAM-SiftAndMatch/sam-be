package com.sam.be.modules.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Theo dõi cooldown trong RAM cho từng Key và từng cặp (Key, Model) để tránh lặp lại key hỏng
 * (401/403) hoặc cặp hết hạn mức (429), đồng thời giữ nguyên strict priority.
 */
@Component
public class AiHealthTracker {

    public static final long QUOTA_COOLDOWN_MS = 5 * 60 * 1000L;
    public static final long DEAD_KEY_COOLDOWN_MS = 30 * 60 * 1000L;
    private static final int MAX_ENTRIES = 1000;

    private final Map<String, AtomicLong> keyBlockedUntil = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> pairBlockedUntil = new ConcurrentHashMap<>();

    public void markKeyDead(String keyFingerprint) {
        markUntil(keyBlockedUntil, keyFingerprint, DEAD_KEY_COOLDOWN_MS);
    }

    public void markKeyQuotaExhausted(String keyFingerprint) {
        markUntil(keyBlockedUntil, keyFingerprint, QUOTA_COOLDOWN_MS);
    }

    public void markPairQuota(String keyFingerprint, String modelName) {
        markUntil(pairBlockedUntil, pairKey(keyFingerprint, modelName), QUOTA_COOLDOWN_MS);
    }

    public void markPairSuccess(String keyFingerprint, String modelName) {
        if (keyFingerprint != null) {
            AtomicLong k = keyBlockedUntil.get(keyFingerprint);
            if (k != null) k.set(0);
            if (modelName != null) {
                AtomicLong p = pairBlockedUntil.get(pairKey(keyFingerprint, modelName));
                if (p != null) p.set(0);
            }
        }
    }

    public boolean isKeyBlocked(String keyFingerprint) {
        return isBlocked(keyBlockedUntil, keyFingerprint);
    }

    public boolean isPairBlocked(String keyFingerprint, String modelName) {
        return isBlocked(pairBlockedUntil, pairKey(keyFingerprint, modelName));
    }

    public void clear() {
        keyBlockedUntil.clear();
        pairBlockedUntil.clear();
    }

    /**
     * Giữ nguyên strict priority giữa các mức khác nhau; chỉ xoay vòng các key cùng mức priority.
     * Key đang bị cooldown được đẩy xuống cuối danh sách (không loại bỏ hoàn toàn).
     */
    public List<AiCatalogService.KeyCandidate> orderKeys(
            List<AiCatalogService.KeyCandidate> orderedByPriority, AtomicInteger cursor) {
        if (orderedByPriority == null || orderedByPriority.size() <= 1) {
            return orderedByPriority == null ? List.of() : orderedByPriority;
        }
        List<AiCatalogService.KeyCandidate> healthy = new ArrayList<>(orderedByPriority.size());
        List<AiCatalogService.KeyCandidate> blocked = new ArrayList<>(0);
        for (var candidate : orderedByPriority) {
            if (isKeyBlocked(candidate.fingerprint())) {
                blocked.add(candidate);
            } else {
                healthy.add(candidate);
            }
        }
        List<AiCatalogService.KeyCandidate> out = new ArrayList<>(orderedByPriority.size());
        appendWithSamePriorityRotation(out, healthy, cursor);
        out.addAll(blocked);
        return out;
    }

    /** Đẩy các model đang bị 429 trên key này xuống sau các model còn khỏe của cùng key. */
    public List<String> orderModelsForKey(List<String> orderedModels, String keyFingerprint) {
        if (orderedModels == null || orderedModels.size() <= 1) {
            return orderedModels == null ? List.of() : orderedModels;
        }
        List<String> healthy = new ArrayList<>(orderedModels.size());
        List<String> blocked = new ArrayList<>(0);
        for (String model : orderedModels) {
            if (isPairBlocked(keyFingerprint, model)) {
                blocked.add(model);
            } else {
                healthy.add(model);
            }
        }
        if (blocked.isEmpty()) return orderedModels;
        healthy.addAll(blocked);
        return healthy;
    }

    private void appendWithSamePriorityRotation(
            List<AiCatalogService.KeyCandidate> dest,
            List<AiCatalogService.KeyCandidate> source,
            AtomicInteger cursor) {
        int i = 0;
        while (i < source.size()) {
            int prio = source.get(i).priority();
            int j = i + 1;
            while (j < source.size() && source.get(j).priority() == prio) {
                j++;
            }
            int groupSize = j - i;
            if (groupSize == 1 || cursor == null) {
                dest.add(source.get(i));
            } else {
                int start = Math.floorMod(cursor.get(), groupSize);
                for (int offset = 0; offset < groupSize; offset++) {
                    dest.add(source.get(i + ((start + offset) % groupSize)));
                }
            }
            i = j;
        }
    }

    private void markUntil(Map<String, AtomicLong> store, String key, long durationMs) {
        if (key == null || key.isBlank()) return;
        long until = System.currentTimeMillis() + Math.max(0L, durationMs);
        store.computeIfAbsent(key, ignored -> new AtomicLong(0)).accumulateAndGet(until, Math::max);
        if (store.size() > MAX_ENTRIES) {
            long now = System.currentTimeMillis();
            store.entrySet().removeIf(e -> now >= e.getValue().get());
        }
    }

    private boolean isBlocked(Map<String, AtomicLong> store, String key) {
        if (key == null) return false;
        AtomicLong until = store.get(key);
        return until != null && System.currentTimeMillis() < until.get();
    }

    private static String pairKey(String keyFingerprint, String modelName) {
        return keyFingerprint + "::" + modelName;
    }
}
