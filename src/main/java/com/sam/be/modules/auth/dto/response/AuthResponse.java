package com.sam.be.modules.auth.dto.response;

import java.util.UUID;
import lombok.Builder;

@Builder
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String email,
        String fullName,
        String role) {}
