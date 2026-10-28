package com.sam.be.modules.auth.dto.response;

import java.util.UUID;

/** Web response: refresh tokens are only transported in an HttpOnly cookie. */
public record PublicAuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String email,
        String fullName,
        String role) {
    public static PublicAuthResponse from(AuthResponse source) {
        return new PublicAuthResponse(
                source.accessToken(),
                source.tokenType(),
                source.expiresIn(),
                source.userId(),
                source.email(),
                source.fullName(),
                source.role());
    }
}
