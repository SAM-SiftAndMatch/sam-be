package com.sam.be.modules.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiHealthTrackerTest {

    @Test
    void keepsStrictPriorityWhileRotatingSamePriorityAndPushingBlockedLast() {
        var tracker = new AiHealthTracker();
        var k1 = new AiCatalogService.KeyCandidate(10, "fp1", "k1");
        var k2a = new AiCatalogService.KeyCandidate(50, "fp2a", "k2a");
        var k2b = new AiCatalogService.KeyCandidate(50, "fp2b", "k2b");
        var k3 = new AiCatalogService.KeyCandidate(100, "fp3", "k3");
        List<AiCatalogService.KeyCandidate> input = List.of(k1, k2a, k2b, k3);

        var cursor = new AtomicInteger(0);
        assertEquals(List.of(k1, k2a, k2b, k3), tracker.orderKeys(input, cursor));

        cursor.set(1);
        // k1 stays first (strict priority 10 < 50), k2a/k2b rotate at priority 50, k3 stays last.
        assertEquals(List.of(k1, k2b, k2a, k3), tracker.orderKeys(input, cursor));

        tracker.markKeyDead("fp1");
        cursor.set(0);
        assertEquals(List.of(k2a, k2b, k3, k1), tracker.orderKeys(input, cursor));
    }

    @Test
    void pushesQuotaBlockedModelBehindHealthyModelsForKey() {
        var tracker = new AiHealthTracker();
        List<String> models = List.of("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash");

        tracker.markPairQuota("fp1", "gemini-2.5-pro");
        assertEquals(
                List.of("gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-pro"),
                tracker.orderModelsForKey(models, "fp1"));
        // Other keys remain unaffected.
        assertEquals(models, tracker.orderModelsForKey(models, "fp2"));
    }
}
