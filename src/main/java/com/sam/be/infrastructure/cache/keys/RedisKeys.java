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
    private static final String PREFIX_JOB_FREELANCER = "job:freelancer:";

    public static String jobDetail(UUID jobId) {
        return PREFIX_JOB_DETAIL + jobId;
    }

    public static String clientJobs(UUID clientId) {
        return PREFIX_JOB_CLIENT + clientId;
    }

    public static String jobRecommendations(UUID jobId) {
        return PREFIX_JOB_REC + jobId;
    }

    public static String openJobs() {
        return "job:open";
    }

    public static String openJobsPage(int page, int size, String sort) {
        String key = page(openJobs(), page, size);
        return "newest".equals(sort) ? key : key + ":sort:" + sort;
    }

    public static String page(String baseKey, int page, int size) {
        return baseKey + ":page:" + page + ":" + size;
    }

    public static String pagesPattern(String baseKey) {
        return baseKey + ":page:*";
    }

    public static String chatRooms(UUID userId) {
        return "chat:rooms:" + userId;
    }

    public static String freelancerProjects(UUID freelancerId) {
        return PREFIX_JOB_FREELANCER + freelancerId;
    }

    public static String freelancerProjectsPage(
            UUID freelancerId, String group, int page, int size) {
        String tab = group == null || group.isBlank() ? "all" : group;
        return page(freelancerProjects(freelancerId) + ":group:" + tab, page, size);
    }

    public static String freelancerProjectsPagesPattern(UUID freelancerId) {
        return freelancerProjects(freelancerId) + ":*";
    }

    public static String proposalsByJob(UUID jobId) {
        return "proposal:job:" + jobId;
    }

    public static String proposalsByFreelancer(UUID freelancerId) {
        return "proposal:freelancer:" + freelancerId;
    }

    public static String workflowTimeline(UUID jobId) {
        return "workflow:timeline:" + jobId;
    }

    public static String workflowTimelineSummary(UUID jobId) {
        return "workflow:timeline:summary:" + jobId;
    }

    public static String workflowDeliveries(UUID jobId) {
        return "workflow:deliveries:" + jobId;
    }

    public static String workflowQaPlan(UUID deliveryId) {
        return "workflow:qa:" + deliveryId;
    }

    public static String completedContractByJob(UUID jobId) {
        return "contract:completed:job:" + jobId;
    }

    public static String contractRevisions(UUID roomId) {
        return "contract:revisions:room:" + roomId;
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

    public static String freelancerEscrowSummary(UUID freelancerId) {
        return "payment:freelancer:escrow-summary:" + freelancerId;
    }

    public static String contractPayments(UUID contractId) {
        return PREFIX_PAYMENT_CONTRACT + contractId;
    }
}
