package com.sam.be.common.security.config;

import java.util.UUID;

/**
 * Interface cho phép SessionGuardService kiểm tra tính hợp lệ của session trong Database khi không
 * tìm thấy trong Redis Cache.
 */
public interface SessionValidator {
    boolean isSessionActive(UUID sessionId);
}
