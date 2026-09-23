package com.sam.be.common.security.util;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<Authentication> getAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
    }

    public static Optional<UUID> getCurrentUserIdOptional() {
        return getAuthentication()
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> (JwtAuthenticationToken) auth)
                .map(jwtAuth -> jwtAuth.getToken().getClaimAsString("user_id"))
                .map(UUID::fromString);
    }

    public static UUID getCurrentUserId() {
        return getCurrentUserIdOptional()
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    public static Optional<UUID> getCurrentSessionIdOptional() {
        return getAuthentication()
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> (JwtAuthenticationToken) auth)
                .map(jwtAuth -> jwtAuth.getToken().getClaimAsString("session_id"))
                .map(UUID::fromString);
    }

    public static UUID getCurrentSessionId() {
        return getCurrentSessionIdOptional()
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    public static Optional<String> getCurrentAccountType() {
        return getAuthentication()
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> (JwtAuthenticationToken) auth)
                .map(jwtAuth -> jwtAuth.getToken().getClaimAsString("account_type"));
    }

    public static Optional<String> getCurrentUserEmail() {
        return getAuthentication()
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> (JwtAuthenticationToken) auth)
                .map(jwtAuth -> jwtAuth.getToken().getSubject());
    }

    public static boolean hasRole(String role) {
        String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return getAuthentication()
                .map(
                        auth ->
                                auth.getAuthorities().stream()
                                        .anyMatch(
                                                a ->
                                                        a.getAuthority()
                                                                .equalsIgnoreCase(roleWithPrefix)))
                .orElse(false);
    }

    public static boolean isAdmin() {
        return hasRole("ROLE_ADMIN");
    }
}
