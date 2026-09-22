package com.sam.be.modules.auth.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.modules.auth.dto.request.LoginRequest;
import com.sam.be.modules.auth.dto.request.RefreshTokenRequest;
import com.sam.be.modules.auth.dto.request.RegisterRequest;
import com.sam.be.modules.auth.dto.response.AuthResponse;
import com.sam.be.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request, httpRequest);
        return ApiResponse.<AuthResponse>builder()
                .result(response)
                .message("Login successful")
                .build();
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(request, httpRequest);
        return ApiResponse.<AuthResponse>builder()
                .result(response)
                .message("Registration successful")
                .build();
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.refresh(request.refreshToken(), httpRequest);
        return ApiResponse.<AuthResponse>builder()
                .result(response)
                .message("Token refreshed successfully")
                .build();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        if (jwt != null) {
            String sessionId = jwt.getClaimAsString("session_id");
            if (sessionId != null) {
                authService.logout(UUID.fromString(sessionId));
            }
        }
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
}
