package com.sam.be.infrastructure.cache.keys;

import java.util.UUID;

public final class RedisKeys {

    private RedisKeys() {}

    private static final String PREFIX_SESSION_ACTIVE = "session:active:";
    private static final String PREFIX_SESSION_REVOKED = "session:revoked:";
    private static final String PREFIX_SESSION_AUTHZ = "session:authz:";

    public static String sessionActive(UUID sessionId) {
        return PREFIX_SESSION_ACTIVE + sessionId;
    }

    public static String sessionRevoked(UUID sessionId) {
        return PREFIX_SESSION_REVOKED + sessionId;
    }

    public static String sessionAuthz(UUID sessionId) {
        return PREFIX_SESSION_AUTHZ + sessionId;
    }
}
