package com.sam.be.infrastructure.cache.keys;

import java.util.UUID;

public final class RedisKeys {

    private RedisKeys() {}

    private static final String PREFIX_SESSION_ACTIVE = "session:active:";
    private static final String PREFIX_SESSION_REVOKED = "session:revoked:";
    private static final String PREFIX_SESSION_AUTHZ = "session:authz:";
    private static final String PREFIX_SESSION_GRACE = "session:grace:";

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

    public static String sessionGrace(UUID sessionId) {
        return PREFIX_SESSION_GRACE + sessionId;
    }

    public static String rateLimitUser(String action, UUID userId) {
        return PREFIX_RATE_LIMIT_USER + action + ":" + userId;
    }

    public static String rateLimitUser(UUID userId) {
        return PREFIX_RATE_LIMIT_USER + userId;
    }

    public static String rateLimitIp(String action, String ip) {
        return PREFIX_RATE_LIMIT_IP + action + ":" + ip;
    }

    public static String rateLimitIp(String ip) {
        return PREFIX_RATE_LIMIT_IP + ip;
    }

    public static String rateLimitField(String action, String field) {
        return PREFIX_RATE_LIMIT_FIELD + action + ":" + field;
    }

    public static String rateLimitField(String field) {
        return PREFIX_RATE_LIMIT_FIELD + field;
    }

    // ==========================================
    // MODULE: SKILL
    // ==========================================
    private static final String PREFIX_SKILL = "skill:";

    public static String skillAll() {
        return PREFIX_SKILL + "all";
    }

    public static String skillSearch(String query) {
        return PREFIX_SKILL + "search:" + (query != null ? query.trim().toLowerCase() : "");
    }

    public static String skillPattern() {
        return PREFIX_SKILL + "*";
    }

    // ==========================================
    // MODULE: USER / PROFILE
    // ==========================================
    private static final String PREFIX_PROFILE_FREELANCER = "profile:freelancer:";
    private static final String PREFIX_PROFILE_CLIENT = "profile:client:";

    public static String freelancerProfile(UUID userId) {
        return PREFIX_PROFILE_FREELANCER + userId;
    }

    public static String clientProfile(UUID userId) {
        return PREFIX_PROFILE_CLIENT + userId;
    }

    // ==========================================
    // MODULE: JOB
    // ==========================================
    private static final String PREFIX_JOB_DETAIL = "job:detail:";
    private static final String PREFIX_JOB_CLIENT = "job:client:";
    private static final String PREFIX_JOB_REC = "job:rec:";

    public static String jobDetail(UUID jobId) {
        return PREFIX_JOB_DETAIL + jobId;
    }

    public static String clientJobs(UUID clientId) {
        return PREFIX_JOB_CLIENT + clientId;
    }

    public static String jobRecommendations(UUID jobId) {
        return PREFIX_JOB_REC + jobId;
    }

    // ==========================================
    // MODULE: SUBSCRIPTION
    // ==========================================
    private static final String PREFIX_SUB_USER = "subscription:user:";

    public static String userSubscriptions(UUID userId) {
        return PREFIX_SUB_USER + userId;
    }

    // ==========================================
    // MODULE: PAYMENT
    // ==========================================
    private static final String PREFIX_PAYMENT_CONTRACT = "payment:contract:";

    public static String contractPayments(UUID contractId) {
        return PREFIX_PAYMENT_CONTRACT + contractId;
    }
}
