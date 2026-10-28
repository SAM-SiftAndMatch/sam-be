package com.sam.be.modules.auth.controller;

import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.annotation.RateLimit;
import com.sam.be.common.security.jwt.JwtProperties;
import com.sam.be.modules.auth.config.GoogleOAuthProperties;
import com.sam.be.modules.auth.dto.response.AuthResponse;
import com.sam.be.modules.auth.dto.response.PublicAuthResponse;
import com.sam.be.modules.auth.service.AuthCookieService;
import com.sam.be.modules.auth.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/google")
@RequiredArgsConstructor
public class GoogleOAuthController {
    private static final String STATE_COOKIE = "sam_google_state";
    private static final String PENDING_COOKIE = "sam_google_pending";
    private final GoogleOAuthService google;
    private final GoogleOAuthProperties properties;
    private final JwtProperties jwtProperties;
    private final AuthCookieService authCookieService;

    public record CompleteRequest(UserRole role, boolean acceptedTerms) {}

    public record LinkRequest(String password) {}

    public record LinkStartResponse(String authorizationUrl) {}

    public record LinkStatusResponse(boolean linked) {}

    @RateLimit(action = "google-login", limit = 10, duration = 60, type = RateLimit.Type.IP_ADDRESS)
    @GetMapping("/start")
    public ResponseEntity<Void> start(HttpServletResponse response) {
        GoogleOAuthService.Start start = google.startLogin();
        writeCookie(response, PENDING_COOKIE, "", "/api/v1/auth/google", 0);
        writeCookie(response, STATE_COOKIE, start.state(), "/api/v1/auth/google/callback", 300);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(start.url())).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @CookieValue(name = STATE_COOKIE, required = false) String browserState,
            HttpServletRequest request,
            HttpServletResponse response) {
        writeCookie(response, STATE_COOKIE, "", "/api/v1/auth/google/callback", 0);
        writeCookie(response, PENDING_COOKIE, "", "/api/v1/auth/google", 0);
        if (!properties.isConfigured()) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_NOT_CONFIGURED);
        }
        if (error != null) {
            return redirect("/login?google_error=cancelled");
        }
        try {
            GoogleOAuthService.Callback result = google.callback(code, state, browserState, request);
            return switch (result.outcome()) {
                case SIGNED_IN -> {
                    authCookieService.setRefreshToken(response, result.auth().refreshToken());
                    yield redirect("/auth/google/return");
                }
                case SIGN_UP -> {
                    writeCookie(response, PENDING_COOKIE, result.pendingTicket(), "/api/v1/auth/google", 600);
                    yield redirect("/auth/google/complete");
                }
                case LINK_REQUIRED -> {
                    writeCookie(response, PENDING_COOKIE, result.pendingTicket(), "/api/v1/auth/google", 600);
                    yield redirect("/login?google_link=1");
                }
                case LINKED -> redirect("/auth/google/return?linked=1");
            };
        } catch (Exception e) {
            log.warn("Google sign-in callback rejected: {}", e.getClass().getSimpleName());
            return redirect("/login?google_error=failed");
        }
    }

    @RateLimit(action = "google-complete", limit = 5, duration = 60, type = RateLimit.Type.IP_ADDRESS)
    @PostMapping("/complete")
    public ApiResponse<PublicAuthResponse> complete(
            @RequestBody CompleteRequest body,
            @CookieValue(name = PENDING_COOKIE, required = false) String ticket,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireFrontendOrigin(request);
        if (body == null || body.role() == null || !body.acceptedTerms()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        AuthResponse auth = google.completeRegistration(ticket, body.role(), request);
        writeCookie(response, PENDING_COOKIE, "", "/api/v1/auth/google", 0);
        authCookieService.setRefreshToken(response, auth.refreshToken());
        return ApiResponse.<PublicAuthResponse>builder()
                .result(PublicAuthResponse.from(auth))
                .build();
    }

    @RateLimit(action = "google-link-pending", limit = 5, duration = 60, type = RateLimit.Type.USER_ID)
    @PostMapping("/link-pending")
    public ApiResponse<Void> linkPending(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody LinkRequest body,
            @CookieValue(name = PENDING_COOKIE, required = false) String ticket,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireFrontendOrigin(request);
        if (jwt == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        google.linkPending(
                ticket,
                UUID.fromString(jwt.getClaimAsString("user_id")),
                body == null ? null : body.password());
        writeCookie(response, PENDING_COOKIE, "", "/api/v1/auth/google", 0);
        return ApiResponse.<Void>builder().message("Google account linked").build();
    }

    @RateLimit(action = "google-link", limit = 5, duration = 60, type = RateLimit.Type.USER_ID)
    @PostMapping("/link/start")
    public ApiResponse<LinkStartResponse> startLink(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody LinkRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireFrontendOrigin(request);
        if (jwt == null || body == null || body.password() == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        GoogleOAuthService.Start start =
                google.startLink(UUID.fromString(jwt.getClaimAsString("user_id")), body.password());
        writeCookie(response, PENDING_COOKIE, "", "/api/v1/auth/google", 0);
        writeCookie(response, STATE_COOKIE, start.state(), "/api/v1/auth/google/callback", 300);
        return ApiResponse.<LinkStartResponse>builder()
                .result(new LinkStartResponse(start.url()))
                .build();
    }

    @GetMapping("/status")
    public ApiResponse<LinkStatusResponse> status(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return ApiResponse.<LinkStatusResponse>builder()
                .result(new LinkStatusResponse(
                        google.isLinked(UUID.fromString(jwt.getClaimAsString("user_id")))))
                .build();
    }

    private ResponseEntity<Void> redirect(String path) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(properties.getFrontendOrigin() + path))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private void requireFrontendOrigin(HttpServletRequest request) {
        if (!properties.isConfigured()
                || !properties.getFrontendOrigin().equals(request.getHeader(HttpHeaders.ORIGIN))) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
    }

    private void writeCookie(
            HttpServletResponse response, String name, String value, String path, long maxAge) {
        ResponseCookie cookie =
                ResponseCookie.from(name, value)
                        .httpOnly(true)
                        .secure(jwtProperties.isCookieSecure())
                        .sameSite("Lax")
                        .path(path)
                        .maxAge(maxAge)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
