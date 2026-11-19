package com.sam.be.modules.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.exception.ApiException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class GooglePasswordSetupStoreTest {
    @Test
    @SuppressWarnings("unchecked")
    void proofExpiresAfterFiveMinutesAndIsConsumedAtomicallyOnce() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Map<String, String> data = new HashMap<>();
        doAnswer(
                        call -> {
                            data.put(call.getArgument(0), call.getArgument(1));
                            return null;
                        })
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(call -> data.get(call.getArgument(0)));
        when(values.getAndDelete(anyString())).thenAnswer(call -> data.remove(call.getArgument(0)));
        var store = new GoogleOAuthStateStore(redis, new ObjectMapper());
        var userId = UUID.randomUUID();
        store.savePasswordSetupGrant("ticket", userId);
        verify(values)
                .set(
                        eq("google:oauth:password-setup:ticket"),
                        anyString(),
                        eq(Duration.ofMinutes(5)));
        assertEquals(userId, store.getPasswordSetupGrant("ticket").orElseThrow().userId());
        assertTrue(store.takePasswordSetupGrant("ticket").isPresent());
        assertTrue(store.takePasswordSetupGrant("ticket").isEmpty());
        assertTrue(store.getPasswordSetupGrant("ticket").isEmpty());
        verify(values, times(2)).getAndDelete("google:oauth:password-setup:ticket");
    }

    @Test
    void missingCookieNeverLooksUpARedisKey() {
        var redis = mock(StringRedisTemplate.class);
        var store = new GoogleOAuthStateStore(redis, new ObjectMapper());
        assertTrue(store.getPasswordSetupGrant(null).isEmpty());
        assertTrue(store.takePasswordSetupGrant(" ").isEmpty());
        verifyNoInteractions(redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void corruptProofFailsClosed() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenReturn("not-json");
        var store = new GoogleOAuthStateStore(redis, new ObjectMapper());
        assertThrows(ApiException.class, () -> store.takePasswordSetupGrant("ticket"));
    }
}
