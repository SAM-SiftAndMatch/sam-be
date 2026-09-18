package com.sam.be.common.security.config;

import com.sam.be.common.exception.ApiException;
import com.sam.be.infrastructure.cache.model.SessionAuthzCache;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi Jwt của Spring Security thành JwtAuthenticationToken với đầy đủ Roles và Permissions.
 * Gọi SessionAuthorityResolver để nạp quyền hạn từ Redis Cache / DB theo mô hình Hybrid.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    SessionAuthorityResolver sessionAuthorityResolver;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String userIdRaw = jwt.getClaimAsString("user_id");
        String sessionIdRaw = jwt.getClaimAsString("session_id");
        String accountType = jwt.getClaimAsString("account_type");
        Instant exp = jwt.getExpiresAt();

        if (userIdRaw == null || sessionIdRaw == null || exp == null) {
            throw new JwtException("Missing required JWT claims (user_id, session_id, exp)");
        }

        UUID userId;
        UUID sessionId;
        try {
            userId = UUID.fromString(userIdRaw);
            sessionId = UUID.fromString(sessionIdRaw);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Invalid UUID format in JWT claims", e);
        }

        SessionAuthzCache authz;
        try {
            authz = sessionAuthorityResolver.resolve(sessionId, userId, exp, accountType);
        } catch (ApiException e) {
            throw new InvalidBearerTokenException(e.getMessage(), e);
        }

        List<GrantedAuthority> authorities = new ArrayList<>();

        // Map Roles (ROLE_FREELANCER, ROLE_CLIENT, ROLE_ADMIN, ...)
        if (authz.roles() != null && !authz.roles().isEmpty()) {
            for (String role : authz.roles()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        } else if (authz.accountType() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + authz.accountType()));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_FREELANCER"));
        }

        // Map Permissions chi tiết (PERM_JOB_CREATE, PERM_PROPOSAL_SUBMIT, ...)
        if (authz.permissions() != null) {
            for (String perm : authz.permissions()) {
                authorities.add(new SimpleGrantedAuthority("PERM_" + perm));
            }
        }

        String principalName = jwt.getSubject() != null ? jwt.getSubject() : userId.toString();

        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }
}
