package com.sam.be.common.security.config;

import com.sam.be.infrastructure.cache.model.SessionAuthzCache;
import java.util.UUID;

/**
 * Interface cho phép nạp quyền hạn của người dùng từ Database (khi Cache Miss). Triển khai cụ thể
 * sẽ nằm ở module auth/user khi kết nối DB.
 */
public interface AuthorityLoader {
    SessionAuthzCache load(UUID userId);
}
