package com.sam.be.modules.auth.service;

import com.sam.be.common.security.jwt.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieService {
    public static final String PASSWORD_SETUP_COOKIE = "sam_password_setup";
    public static final String PASSWORD_SETUP_FLOW_COOKIE = "sam_password_setup_flow";
    private final JwtProperties jwtProperties;

    public void setRefreshToken(HttpServletResponse response, String refreshToken) {
        writeRefreshCookie(response, refreshToken, jwtProperties.getRefreshExpiration());
    }

    public void clearRefreshToken(HttpServletResponse response) {
        writeRefreshCookie(response, "", 0);
    }

    public void setPasswordSetupProof(HttpServletResponse response, String ticket) {
        writeCookie(response, PASSWORD_SETUP_COOKIE, ticket, "/api/v1/auth/password", 300);
    }

    public void clearPasswordSetupProof(HttpServletResponse response) {
        writeCookie(response, PASSWORD_SETUP_COOKIE, "", "/api/v1/auth/password", 0);
    }

    public void setPasswordSetupFlow(HttpServletResponse response, boolean active) {
        writeCookie(
                response,
                PASSWORD_SETUP_FLOW_COOKIE,
                active ? "1" : "",
                "/api/v1/auth/google/callback",
                active ? 300 : 0);
    }

    public void setGoogleState(HttpServletResponse response, String state) {
        writeCookie(response, "sam_google_state", state, "/api/v1/auth/google/callback", 300);
    }

    private void writeCookie(
            HttpServletResponse response, String name, String value, String path, long maxAge) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(name, value)
                        .httpOnly(true)
                        .secure(jwtProperties.isCookieSecure())
                        .sameSite("Lax")
                        .path(path)
                        .maxAge(maxAge)
                        .build()
                        .toString());
    }

    private void writeRefreshCookie(
            HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie =
                ResponseCookie.from("refresh_token", value)
                        .httpOnly(true)
                        .secure(jwtProperties.isCookieSecure())
                        .sameSite("Lax")
                        .path("/api/v1/auth")
                        .maxAge(maxAgeSeconds)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
