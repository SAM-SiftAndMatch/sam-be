package com.sam.be.modules.auth.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.annotation.RateLimit;
import com.sam.be.common.security.jwt.JwtProperties;
import com.sam.be.modules.auth.dto.request.LoginRequest;
import com.sam.be.modules.auth.dto.request.RefreshTokenRequest;
import com.sam.be.modules.auth.dto.request.RegisterRequest;
import com.sam.be.modules.auth.dto.response.AuthResponse;
import com.sam.be.modules.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
    JwtProperties jwtProperties;

    @RateLimit(action = "login", limit = 5, duration = 60, type = RateLimit.Type.IP_ADDRESS)
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthResponse response = authService.login(request, httpRequest);
        setRefreshTokenCookie(httpResponse, response.refreshToken(), 7 * 24 * 60 * 60L);

        return ApiResponse.<AuthResponse>builder()
                .result(response)
                .message("Login successful")
                .build();
    }

    @RateLimit(action = "register", limit = 3, duration = 60, type = RateLimit.Type.IP_ADDRESS)
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthResponse response = authService.register(request, httpRequest);
        setRefreshTokenCookie(httpResponse, response.refreshToken(), 7 * 24 * 60 * 60L);

        return ApiResponse.<AuthResponse>builder()
                .result(response)
                .message("Registration successful")
                .build();
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String token =
                (request != null
                                && request.refreshToken() != null
                                && !request.refreshToken().isBlank())
                        ? request.refreshToken()
                        : extractRefreshTokenFromCookie(httpRequest);

        AuthResponse response = authService.refresh(token, httpRequest);
        setRefreshTokenCookie(httpResponse, response.refreshToken(), 7 * 24 * 60 * 60L);

        return ApiResponse.<AuthResponse>builder()
                .result(response)
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
        setRefreshTokenCookie(httpResponse, "", 0L);

        return ApiResponse.<Void>builder().message("Logged out successfully").build();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.<Map<String, Object>>builder()
                .result(
                        Map.of(
                                "userId", jwt.getClaimAsString("user_id"),
                                "email", jwt.getSubject(),
                                "accountType", jwt.getClaimAsString("account_type"),
                                "sessionId", jwt.getClaimAsString("session_id")))
                .message("Current user retrieved successfully")
                .build();
    }

    private void setRefreshTokenCookie(
            HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        ResponseCookie cookie =
                ResponseCookie.from("refresh_token", refreshToken != null ? refreshToken : "")
                        .httpOnly(true)
                        .secure(jwtProperties.isCookieSecure())
                        .sameSite("Lax")
                        .path("/api/v1/auth")
                        .maxAge(maxAgeSeconds)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
