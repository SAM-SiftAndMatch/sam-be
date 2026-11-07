package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sam.be.common.security.annotation.RateLimit;
import com.sam.be.infrastructure.cache.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class FinanceRateLimitTest {
    @Test
    void sensitiveActionsAreDeniedWhenRedisCannotEnforceLimits() {
        var redis =
                mock(
                        StringRedisTemplate.class,
                        invocation -> {
                            throw new org.springframework.dao.DataAccessResourceFailureException(
                                    "unavailable");
                        });
        var limiter = new RateLimitService(redis);
        assertFalse(limiter.allowRequest("finance-test", 5, 1, 1, true));
        assertTrue(limiter.allowRequest("legacy-test", 5, 1, 1));
    }

    @Test
    void everyPasswordProtectedFinanceEndpointUsesFailClosedRateLimiting() {
        for (var method : FinanceController.class.getDeclaredMethods()) {
            boolean takesPassword =
                    java.util.Arrays.stream(method.getParameterTypes())
                            .filter(Class::isRecord)
                            .flatMap(type -> java.util.Arrays.stream(type.getRecordComponents()))
                            .anyMatch(component -> component.getName().equals("password"));
            if (!takesPassword) continue;
            var limit = method.getAnnotation(RateLimit.class);
            assertNotNull(limit, method.getName());
            assertTrue(limit.failClosed(), method.getName());
        }
    }
}
