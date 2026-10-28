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
    private final JwtProperties jwtProperties;

    public void setRefreshToken(HttpServletResponse response, String refreshToken) {
        writeRefreshCookie(response, refreshToken, jwtProperties.getRefreshExpiration());
    }

    public void clearRefreshToken(HttpServletResponse response) {
        writeRefreshCookie(response, "", 0);
    }

    private void writeRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
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
