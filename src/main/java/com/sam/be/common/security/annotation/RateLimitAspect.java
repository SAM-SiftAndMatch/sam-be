package com.sam.be.common.security.annotation;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.infrastructure.cache.keys.RedisKeys;
import com.sam.be.infrastructure.cache.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class RateLimitAspect {

    RateLimitService rateLimitService;

    @Around("@annotation(rateLimit)")
    public Object handleRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit)
            throws Throwable {

        String fullKey = resolveFullKey(joinPoint, rateLimit);

        long capacity = rateLimit.limit();
        double refillRate = (double) capacity / rateLimit.duration();

        boolean allowed = rateLimitService.allowRequest(fullKey, capacity, refillRate, 1);

        if (!allowed) {
            log.warn("Rate limit exceeded for key: {}", fullKey);
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
        }

        return joinPoint.proceed();
    }

    private String resolveFullKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        String action =
                !rateLimit.action().isBlank()
                        ? rateLimit.action()
                        : joinPoint.getSignature().getName();

        return switch (rateLimit.type()) {
            case IP_ADDRESS -> RedisKeys.rateLimitIp(action, getClientIp());
            case USER_ID -> {
                UUID userId = SecurityUtils.getCurrentUserId();
                yield RedisKeys.rateLimitUser(action, userId);
            }
            case REQUEST_FIELD -> {
                String val = getFieldFromArgs(joinPoint, rateLimit.fieldName());
                yield RedisKeys.rateLimitField(action, val);
            }
        };
    }

    private String getClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        String xReal = request.getHeader("X-Real-IP");
        if (xReal != null && !xReal.isBlank()) {
            return xReal.trim();
        }
        return request.getRemoteAddr();
    }

    private String getFieldFromArgs(ProceedingJoinPoint joinPoint, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return "unknown";
        }

        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }

            // Hỗ trợ Java Records
            if (arg.getClass().isRecord()) {
                for (RecordComponent rc : arg.getClass().getRecordComponents()) {
                    if (rc.getName().equals(fieldName)) {
                        try {
                            Object val = rc.getAccessor().invoke(arg);
                            return val != null ? val.toString() : "unknown";
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            // Hỗ trợ Java Class thông thường
            try {
                Field field = arg.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(arg);
                return value != null ? value.toString() : "unknown";
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException e) {
                log.error("Cannot access field {}", fieldName, e);
            }
        }
        return "unknown";
    }
}
