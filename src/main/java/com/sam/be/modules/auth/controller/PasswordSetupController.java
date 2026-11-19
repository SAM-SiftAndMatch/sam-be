package com.sam.be.modules.auth.controller;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.annotation.RateLimit;
import com.sam.be.modules.auth.config.GoogleOAuthProperties;
import com.sam.be.modules.auth.service.AuthCookieService;
import com.sam.be.modules.auth.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/password")
@RequiredArgsConstructor
public class PasswordSetupController {
    private final GoogleOAuthService google;
    private final GoogleOAuthProperties properties;
    private final AuthCookieService cookies;

    public record SetupInput(
            @NotBlank @Size(min = 8, max = 72) String newPassword,
            @NotBlank @Size(max = 72) String confirmPassword) {
        @Override
        public String toString() {
            return "SetupInput[REDACTED]";
        }
    }

    @GetMapping("/status")
    public ApiResponse<GoogleOAuthService.PasswordSetupStatus> status(
            @AuthenticationPrincipal Jwt jwt,
            @CookieValue(name = AuthCookieService.PASSWORD_SETUP_COOKIE, required = false)
                    String ticket,
            HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.<GoogleOAuthService.PasswordSetupStatus>builder()
                .result(google.passwordSetupStatus(userId(jwt), ticket))
                .build();
    }

    @PostMapping("/setup/start")
    @RateLimit(
            action = "password-setup-start",
            limit = 5,
            duration = 300,
            type = RateLimit.Type.USER_ID,
            failClosed = true)
    public ApiResponse<GoogleOAuthController.LinkStartResponse> start(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireOrigin(request);
        var start = google.startPasswordSetup(userId(jwt));
        cookies.clearPasswordSetupProof(response);
        cookies.setPasswordSetupFlow(response, true);
        cookies.setGoogleState(response, start.state());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.<GoogleOAuthController.LinkStartResponse>builder()
                .result(new GoogleOAuthController.LinkStartResponse(start.url()))
                .build();
    }

    @PostMapping("/setup")
    @RateLimit(
            action = "password-setup",
            limit = 5,
            duration = 300,
            type = RateLimit.Type.USER_ID,
            failClosed = true)
    public ApiResponse<Void> setup(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SetupInput body,
            @CookieValue(name = AuthCookieService.PASSWORD_SETUP_COOKIE, required = false)
                    String ticket,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireOrigin(request);
        google.setupPassword(userId(jwt), ticket, body.newPassword(), body.confirmPassword());
        cookies.clearPasswordSetupProof(response);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.<Void>builder().message("Đã thiết lập mật khẩu SAM thành công.").build();
    }

    private UUID userId(Jwt jwt) {
        if (jwt == null || jwt.getClaimAsString("user_id") == null)
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        return UUID.fromString(jwt.getClaimAsString("user_id"));
    }

    private void requireOrigin(HttpServletRequest request) {
        if (!properties.isConfigured()
                || !properties.getFrontendOrigin().equals(request.getHeader(HttpHeaders.ORIGIN)))
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
    }
}
