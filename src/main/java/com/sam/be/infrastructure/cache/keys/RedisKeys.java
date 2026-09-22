package com.sam.be.infrastructure.cache.keys;

import java.util.UUID;

public final class RedisKeys {

    private RedisKeys() {}

    private static final String PREFIX_SESSION_ACTIVE = "session:active:";
    private static final String PREFIX_SESSION_REVOKED = "session:revoked:";
    private static final String PREFIX_SESSION_AUTHZ = "session:authz:";

    private static final String PREFIX_RATE_LIMIT_USER = "ratelimit:user:";
    private static final String PREFIX_RATE_LIMIT_IP = "ratelimit:ip:";
    private static final String PREFIX_RATE_LIMIT_FIELD = "ratelimit:field:";

    public static String sessionActive(UUID sessionId) {
        return PREFIX_SESSION_ACTIVE + sessionId;
    }

    public static String sessionRevoked(UUID sessionId) {
        return PREFIX_SESSION_REVOKED + sessionId;
    }

    public static String sessionAuthz(UUID sessionId) {
        return PREFIX_SESSION_AUTHZ + sessionId;
    }

    public static String rateLimitUser(UUID userId) {
        return PREFIX_RATE_LIMIT_USER + userId;
    }

    public static String rateLimitIp(String ip) {
        return PREFIX_RATE_LIMIT_IP + ip;
    }

    public static String rateLimitField(String field) {
        return PREFIX_RATE_LIMIT_FIELD + field;
    }
}
