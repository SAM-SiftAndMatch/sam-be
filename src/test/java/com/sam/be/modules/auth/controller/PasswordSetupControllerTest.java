package com.sam.be.modules.auth.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.security.jwt.JwtProperties;
import com.sam.be.modules.auth.config.GoogleOAuthProperties;
import com.sam.be.modules.auth.service.AuthCookieService;
import com.sam.be.modules.auth.service.GoogleOAuthService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;

class PasswordSetupControllerTest {
    private final UUID userId = UUID.randomUUID();
    private final GoogleOAuthService google = mock(GoogleOAuthService.class);
    private final GoogleOAuthProperties properties = new GoogleOAuthProperties();
    private final JwtProperties jwtProperties = new JwtProperties();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private PasswordSetupController controller;
    private AuthCookieService cookies;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        properties.setClientId("sam-client");
        properties.setClientSecret("test-secret");
        properties.setRedirectUri("https://api.sam.pro.vn/api/v1/auth/google/callback");
        properties.setFrontendOrigin("https://www.sam.pro.vn");
        jwtProperties.setCookieSecure(true);
        cookies = new AuthCookieService(jwtProperties);
        controller = new PasswordSetupController(google, properties, cookies);
        request.addHeader(HttpHeaders.ORIGIN, properties.getFrontendOrigin());
        jwt =
                Jwt.withTokenValue("sam-token")
                        .header("alg", "HS512")
                        .claim("user_id", userId.toString())
                        .build();
    }

    @Test
    void startRequiresTheExactFrontendOriginAndAnAuthenticatedPrincipal() {
        request.removeHeader(HttpHeaders.ORIGIN);
        assertEquals(
                ErrorCode.FORBIDDEN_ACTION,
                assertThrows(ApiException.class, () -> controller.start(jwt, request, response))
                        .getErrorCode());
        request.addHeader(HttpHeaders.ORIGIN, "https://attacker.example");
        assertThrows(ApiException.class, () -> controller.start(jwt, request, response));
        request.removeHeader(HttpHeaders.ORIGIN);
        request.addHeader(HttpHeaders.ORIGIN, properties.getFrontendOrigin());
        assertEquals(
                ErrorCode.UNAUTHENTICATED,
                assertThrows(ApiException.class, () -> controller.start(null, request, response))
                        .getErrorCode());
        verifyNoInteractions(google);
    }

    @Test
    void startSetsShortLivedHttpOnlyCookiesWithoutReturningTheProof() {
        when(google.startPasswordSetup(userId))
                .thenReturn(
                        new GoogleOAuthService.Start(
                                "oauth-state",
                                "https://accounts.google.com/authorize?state=oauth-state"));
        var result = controller.start(jwt, request, response);
        assertTrue(
                result.getResult().authorizationUrl().startsWith("https://accounts.google.com/"));
        String state = cookie("sam_google_state=oauth-state");
        assertTrue(state.contains("Path=/api/v1/auth/google/callback"));
        assertTrue(state.contains("Max-Age=300"));
        assertTrue(state.contains("HttpOnly"));
        assertTrue(state.contains("Secure"));
        assertTrue(state.contains("SameSite=Lax"));
        assertTrue(cookie("sam_password_setup_flow=1").contains("Max-Age=300"));
        assertTrue(cookie("sam_password_setup=").contains("Max-Age=0"));
        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void statusUsesTheAuthenticatedUserAndIsNotCacheable() {
        var status = new GoogleOAuthService.PasswordSetupStatus(false, true, true);
        when(google.passwordSetupStatus(userId, "proof")).thenReturn(status);
        assertSame(status, controller.status(jwt, "proof", response).getResult());
        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void setupConsumesProofAndClearsTheCookieAfterSuccess() {
        var body = new PasswordSetupController.SetupInput("new-password", "new-password");
        controller.setup(jwt, body, "proof", request, response);
        verify(google).setupPassword(userId, "proof", "new-password", "new-password");
        String cleared = cookie("sam_password_setup=");
        assertTrue(cleared.contains("Path=/api/v1/auth/password"));
        assertTrue(cleared.contains("Max-Age=0"));
        assertFalse(body.toString().contains("new-password"));
        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
    }

    @Test
    void callbackStoresProofOnlyInCookieAndRedirectsToFixedSetupRoute() {
        var callback = new GoogleOAuthController(google, properties, jwtProperties, cookies);
        when(google.callback("code", "state", "state", request))
                .thenReturn(
                        new GoogleOAuthService.Callback(
                                GoogleOAuthService.Outcome.PASSWORD_SETUP_VERIFIED,
                                null,
                                "one-use-proof"));
        var result = callback.callback("code", "state", null, "state", "1", request, response);
        assertEquals(
                "https://www.sam.pro.vn/account/password/setup?verified=1",
                result.getHeaders().getLocation().toString());
        assertFalse(result.getHeaders().getLocation().toString().contains("one-use-proof"));
        String proof = cookie("sam_password_setup=one-use-proof");
        assertTrue(proof.contains("HttpOnly"));
        assertTrue(proof.contains("Secure"));
        assertTrue(proof.contains("Max-Age=300"));
        assertTrue(proof.contains("Path=/api/v1/auth/password"));
    }

    @Test
    void callbackFailureAndCancellationReturnToSetupWithoutCompletingGoogleSignup() {
        var callback = new GoogleOAuthController(google, properties, jwtProperties, cookies);
        var cancelled =
                callback.callback(null, "state", "access_denied", "state", "1", request, response);
        assertEquals(
                "https://www.sam.pro.vn/account/password/setup?error=cancelled",
                cancelled.getHeaders().getLocation().toString());
        verifyNoInteractions(google);
        when(google.callback("code", "state", "state", request))
                .thenThrow(new ApiException(ErrorCode.GOOGLE_AUTH_FAILED));
        var failed = callback.callback("code", "state", null, "state", "1", request, response);
        assertEquals(
                "https://www.sam.pro.vn/account/password/setup?error=failed",
                failed.getHeaders().getLocation().toString());
        assertTrue(
                response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                        .noneMatch(value -> value.startsWith("sam_password_setup=one-use-proof")));
    }

    @Test
    void ordinaryGoogleLoginCallbackKeepsItsExistingDestination() {
        var callback = new GoogleOAuthController(google, properties, jwtProperties, cookies);
        var result = callback.callback(null, null, "access_denied", null, null, request, response);
        assertEquals(
                "https://www.sam.pro.vn/login?google_error=cancelled",
                result.getHeaders().getLocation().toString());
    }

    private String cookie(String prefix) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }
}
