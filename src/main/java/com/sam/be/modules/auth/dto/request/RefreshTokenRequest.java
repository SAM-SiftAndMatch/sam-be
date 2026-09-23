package com.sam.be.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required") String refreshToken) {}
