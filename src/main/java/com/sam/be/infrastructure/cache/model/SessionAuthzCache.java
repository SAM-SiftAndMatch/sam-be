package com.sam.be.infrastructure.cache.model;

import java.util.List;
import java.util.UUID;

/**
 * Lưu trữ ngữ cảnh quyền hạn của người dùng trong Redis. Tối ưu cho nền tảng SAM (kết nối
 * Freelancer và Client).
 */
public record SessionAuthzCache(
        UUID userId,
        String accountType, // "FREELANCER" | "CLIENT" | "ADMIN"
        List<String> roles, // ["FREELANCER"], ["CLIENT"], ["ADMIN"]
        List<String> permissions // ["JOB_CREATE", "PROPOSAL_SUBMIT", ...]
        ) {}
