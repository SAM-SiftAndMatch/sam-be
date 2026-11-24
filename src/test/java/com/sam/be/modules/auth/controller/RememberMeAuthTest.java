package com.sam.be.modules.auth.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.security.config.CorsProperties;
import com.sam.be.common.security.jwt.JwtProperties;
import com.sam.be.common.security.jwt.JwtTokenProvider;
import com.sam.be.infrastructure.cache.service.SessionCacheService;
import com.sam.be.modules.auth.config.GoogleOAuthProperties;
import com.sam.be.modules.auth.dto.request.LoginRequest;
import com.sam.be.modules.auth.dto.response.AuthResponse;
import com.sam.be.modules.auth.entity.Session;
import com.sam.be.modules.auth.repository.SessionRepository;
import com.sam.be.modules.auth.service.AuthCookieService;
import com.sam.be.modules.auth.service.GoogleOAuthService;
import com.sam.be.modules.auth.service.impl.AuthServiceImpl;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.ClientProfileRepository;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RememberMeAuthTest {
    private final UserRepository users = mock(UserRepository.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final SessionCacheService cache = mock(SessionCacheService.class);
    private final JwtProperties properties = new JwtProperties();
    private final List<Session> saved = new ArrayList<>();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final User user =
            User.builder()
                    .id(UUID.randomUUID())
                    .email("user@example.com")
                    .passwordHash("hash")
                    .role(UserRole.CLIENT)
                    .isActive(true)
                    .build();
    private AuthServiceImpl service;
    private AuthCookieService cookies;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        properties.setAccessExpiration(900);
        properties.setRefreshExpiration(604800);
        properties.setSessionExpiration(43200);
        properties.setCookieSecure(true);
        properties.setIssuer("sam-test");
        properties.setSignerKeyBase64(Base64.getEncoder().encodeToString(new byte[64]));
        service =
                new AuthServiceImpl(
                        users,
                        sessions,
                        mock(FreelancerProfileRepository.class),
                        mock(ClientProfileRepository.class),
                        passwords,
                        new JwtTokenProvider(properties),
                        properties,
                        cache);
        cookies = new AuthCookieService(properties);
        var cors = new CorsProperties();
        cors.setAllowedOrigins(List.of("https://sam.example"));
        controller = new AuthController(service, cookies, cors);
        request.addHeader(HttpHeaders.ORIGIN, "https://sam.example");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwords.matches("password", "hash")).thenReturn(true);
        when(sessions.save(any(Session.class)))
                .thenAnswer(
                        call -> {
                            Session session = call.getArgument(0);
                            if (session.getId() == null) session.setId(UUID.randomUUID());
                            saved.add(session);
                            return session;
                        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loginCreatesTheSelectedSessionAndHttpOnlyCookie(boolean rememberMe) {
        long before = Instant.now().getEpochSecond();
        var response = new MockHttpServletResponse();
        var result =
                controller.login(
                        new LoginRequest(user.getEmail(), "password", rememberMe),
                        request,
                        response);

        Session session = saved.getFirst();
        assertEquals(rememberMe, session.isRememberMe());
        long lifetime = rememberMe ? 604800 : 43200;
        long expiresAt = session.getExpiresAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        assertTrue(expiresAt >= before + lifetime);
        assertTrue(expiresAt <= Instant.now().getEpochSecond() + lifetime);
        assertEquals(900, result.getResult().expiresIn());
        assertCookieMode(response, rememberMe);
    }

    @Test
    void loginWithoutTheNewFieldDefaultsToABrowserSession() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                "{\"email\":\"user@example.com\",\"password\":\"password\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.result.accessToken").isNotEmpty())
                        .andExpect(jsonPath("$.result.refreshToken").doesNotExist())
                        .andExpect(jsonPath("$.result.refreshExpiresAt").doesNotExist())
                        .andReturn();
        assertFalse(saved.getFirst().isRememberMe());
        assertCookieMode(result.getResponse(), false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loginJsonTransportsTheChoiceToTheServer(boolean rememberMe) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType("application/json")
                                .content(
                                        "{\"email\":\"user@example.com\",\"password\":\"password\",\"rememberMe\":"
                                                + rememberMe
                                                + "}"))
                .andExpect(status().isOk());
        assertEquals(rememberMe, saved.getFirst().isRememberMe());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refreshKeepsTheChoiceAndOriginalDeadline(boolean rememberMe) {
        LocalDateTime deadline = LocalDateTime.now().plusHours(2);
        Session old = existingSession(rememberMe, deadline);
        var response = new MockHttpServletResponse();
        controller.refresh(request, response);

        Session rotated = saved.getLast();
        assertTrue(old.getIsRevoked());
        assertEquals(rememberMe, rotated.isRememberMe());
        assertEquals(deadline, rotated.getExpiresAt());
        assertNotEquals(old.getRefreshToken(), rotated.getRefreshToken());
        assertCookieMode(response, rememberMe);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refreshRetryKeepsTheCookieModeFromTheCachedResponse(boolean rememberMe) throws Exception {
        Session old = existingSession(rememberMe, LocalDateTime.now().plusHours(2));
        AuthResponse rotated = service.refresh(old.getRefreshToken(), request);
        // Redis serializes the internal response; the preference must survive a round trip.
        var mapper = new ObjectMapper();
        var cached = mapper.readValue(mapper.writeValueAsString(rotated), AuthResponse.class);
        when(cache.getGraceResponse(old.getId())).thenReturn(Optional.of(cached));
        saved.clear();
        var response = new MockHttpServletResponse();
        controller.refresh(request, response);
        assertTrue(saved.isEmpty());
        assertCookieMode(response, rememberMe);
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains(rotated.refreshToken()));
    }

    @Test
    void expiredSessionsCannotBeRefreshedEvenIfARetryResponseExists() {
        Session old = existingSession(true, LocalDateTime.now().minusSeconds(1));
        old.setIsRevoked(true);
        ApiException error =
                assertThrows(
                        ApiException.class, () -> service.refresh(old.getRefreshToken(), request));
        assertEquals(ErrorCode.SESSION_EXPIRED, error.getErrorCode());
        assertTrue(saved.isEmpty());
        verifyNoInteractions(cache);
    }

    @Test
    void accessTokenCannotOutliveTheSession() throws Exception {
        LocalDateTime deadline = LocalDateTime.now().plusSeconds(30);
        Session old = existingSession(false, deadline);
        AuthResponse result = service.refresh(old.getRefreshToken(), request);
        assertTrue(result.expiresIn() > 0 && result.expiresIn() <= 30);
        Instant accessExpiry =
                SignedJWT.parse(result.accessToken())
                        .getJWTClaimsSet()
                        .getExpirationTime()
                        .toInstant();
        assertFalse(accessExpiry.isAfter(deadline.atZone(ZoneId.systemDefault()).toInstant()));
    }

    @Test
    void shortSessionNeverExceedsTheConfiguredRefreshLifetime() {
        properties.setRefreshExpiration(3600);
        var result = service.login(new LoginRequest(user.getEmail(), "password", false), request);
        assertTrue(result.refreshExpiresAt() <= Instant.now().getEpochSecond() + 3600);
    }

    @Test
    void logoutExpiresTheCookieForEitherSessionType() {
        var response = new MockHttpServletResponse();
        cookies.clearRefreshToken(response);
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void googleCallbackWritesTheSameCookieModeAsPasswordLogin(boolean rememberMe) {
        var google = mock(GoogleOAuthService.class);
        var config = new GoogleOAuthProperties();
        config.setClientId("client-id");
        config.setClientSecret("secret");
        config.setRedirectUri("https://api.sam.example/api/v1/auth/google/callback");
        config.setFrontendOrigin("https://sam.example");
        var googleController = new GoogleOAuthController(google, config, properties, cookies);
        var auth =
                AuthResponse.builder()
                        .refreshToken("google-refresh-token")
                        .rememberMe(rememberMe)
                        .refreshExpiresAt(Instant.now().getEpochSecond() + 3600)
                        .build();
        when(google.callback("code", "state", "state", request))
                .thenReturn(
                        new GoogleOAuthService.Callback(
                                GoogleOAuthService.Outcome.SIGNED_IN, auth, null));
        var response = new MockHttpServletResponse();
        googleController.callback("code", "state", null, "state", null, request, response);
        String cookie =
                response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                        .filter(value -> value.startsWith("refresh_token="))
                        .findFirst()
                        .orElseThrow();
        assertEquals(rememberMe, cookie.contains("Max-Age="));
        assertTrue(cookie.contains("HttpOnly"));
    }

    private Session existingSession(boolean rememberMe, LocalDateTime deadline) {
        Session session =
                Session.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .refreshToken("old-refresh-token")
                        .rememberMe(rememberMe)
                        .expiresAt(deadline)
                        .build();
        when(sessions.findByRefreshToken(session.getRefreshToken()))
                .thenReturn(Optional.of(session));
        request.setCookies(new Cookie("refresh_token", session.getRefreshToken()));
        return session;
    }

    private void assertCookieMode(MockHttpServletResponse response, boolean rememberMe) {
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith("refresh_token="));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/api/v1/auth"));
        if (rememberMe) {
            assertTrue(cookie.contains("Max-Age="));
            assertFalse(cookie.contains("Max-Age=0"));
        } else {
            assertFalse(cookie.contains("Max-Age"));
            assertFalse(cookie.contains("Expires="));
        }
    }
}
