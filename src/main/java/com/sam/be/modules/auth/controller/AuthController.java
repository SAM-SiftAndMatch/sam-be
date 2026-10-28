package com.sam.be.modules.auth.controller;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.annotation.RateLimit;
import com.sam.be.common.security.config.CorsProperties;
import com.sam.be.modules.auth.dto.request.LoginRequest;
import com.sam.be.modules.auth.dto.request.RegisterRequest;
import com.sam.be.modules.auth.dto.response.AuthResponse;
import com.sam.be.modules.auth.dto.response.PublicAuthResponse;
import com.sam.be.modules.auth.service.AuthCookieService;
import com.sam.be.modules.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthController {

    AuthService authService;
    AuthCookieService authCookieService;
    CorsProperties corsProperties;

    @RateLimit(action = "login", limit = 5, duration = 60, type = RateLimit.Type.IP_ADDRESS)
    @PostMapping("/login")
    public ApiResponse<PublicAuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthResponse response = authService.login(request, httpRequest);
        authCookieService.setRefreshToken(httpResponse, response.refreshToken());

        return ApiResponse.<PublicAuthResponse>builder()
                .result(PublicAuthResponse.from(response))
                .message("Login successful")
                .build();
    }

    @RateLimit(action = "register", limit = 3, duration = 60, type = RateLimit.Type.IP_ADDRESS)
    @PostMapping("/register")
    public ApiResponse<PublicAuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthResponse response = authService.register(request, httpRequest);
        authCookieService.setRefreshToken(httpResponse, response.refreshToken());

        return ApiResponse.<PublicAuthResponse>builder()
                .result(PublicAuthResponse.from(response))
                .message("Registration successful")
                .build();
    }

    @PostMapping("/refresh")
    public ApiResponse<PublicAuthResponse> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (!corsProperties.getAllowedOrigins().contains(httpRequest.getHeader(HttpHeaders.ORIGIN))) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        AuthResponse response = authService.refresh(extractRefreshTokenFromCookie(httpRequest), httpRequest);
        authCookieService.setRefreshToken(httpResponse, response.refreshToken());

        return ApiResponse.<PublicAuthResponse>builder()
                .result(PublicAuthResponse.from(response))
                .message("Token refreshed successfully")
                .build();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal Jwt jwt, HttpServletResponse httpResponse) {
        if (jwt != null) {
            String sessionId = jwt.getClaimAsString("session_id");
            if (sessionId != null) {
                authService.logout(UUID.fromString(sessionId));
            }
        }
        // Xóa refresh token cookie trên trình duyệt
        authCookieService.clearRefreshToken(httpResponse);

        return ApiResponse.<Void>builder().message("Logged out successfully").build();
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> "refresh_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
