package com.sam.be.modules.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.auth.config.GoogleOAuthProperties;
import com.sam.be.modules.auth.dto.response.AuthResponse;
import com.sam.be.modules.auth.entity.GoogleIdentity;
import com.sam.be.modules.auth.repository.GoogleIdentityRepository;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.ClientProfileRepository;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

class GoogleOAuthServiceTest {
    private final GoogleOAuthStateStore states = mock(GoogleOAuthStateStore.class);
    private final GoogleIdentityRepository identities = mock(GoogleIdentityRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ClientProfileRepository clients = mock(ClientProfileRepository.class);
    private final FreelancerProfileRepository freelancers = mock(FreelancerProfileRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final RestClient restClient = mock(RestClient.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private GoogleOAuthService service;

    @BeforeEach
    void setUp() {
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setClientId("sam-client.apps.googleusercontent.com");
        properties.setClientSecret("test-secret");
        properties.setRedirectUri("https://api.sam.pro.vn/api/v1/auth/google/callback");
        properties.setFrontendOrigin("https://sam.pro.vn");
        service = new GoogleOAuthService(
                properties, states, identities, users, clients, freelancers,
                auth, passwords, restClient, new ObjectMapper());
    }

    @Test
    void startCreatesOneTimeStateNonceAndPkceChallenge() {
        GoogleOAuthService.Start start = service.startLogin();

        ArgumentCaptor<GoogleOAuthStateStore.Attempt> saved =
                ArgumentCaptor.forClass(GoogleOAuthStateStore.Attempt.class);
        verify(states).saveAttempt(org.mockito.ArgumentMatchers.eq(start.state()), saved.capture());
        assertEquals("LOGIN", saved.getValue().intent());
        assertNotNull(saved.getValue().nonce());
        assertFalse(saved.getValue().verifier().isBlank());
        assertFalse(start.url().contains(saved.getValue().verifier()));
        assertFalse(start.url().contains("test-secret"));
        org.junit.jupiter.api.Assertions.assertTrue(start.url().contains("code_challenge_method=S256"));
    }

    @Test
    void callbackRejectsBrowserStateMismatchBeforeUsingCode() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.callback("google-code", "expected", "different", request));

        assertEquals(ErrorCode.GOOGLE_AUTH_FAILED, error.getErrorCode());
        verifyNoInteractions(states, restClient);
    }

    @Test
    void verifiedGoogleClaimsRequireMatchingAudienceNonceAndVerifiedEmail() {
        Jwt valid = googleJwt("sam-client.apps.googleusercontent.com", "expected-nonce", true);
        var identity = service.validateIdentity(valid, "expected-nonce");
        assertEquals("google-sub", identity.subject());
        assertEquals("user@gmail.com", identity.email());

        assertThrows(ApiException.class,
                () -> service.validateIdentity(valid, "wrong-nonce"));
        assertThrows(ApiException.class,
                () -> service.validateIdentity(
                        googleJwt("other-client.apps.googleusercontent.com", "expected-nonce", true),
                        "expected-nonce"));
        assertThrows(ApiException.class,
                () -> service.validateIdentity(
                        googleJwt("sam-client.apps.googleusercontent.com", "expected-nonce", false),
                        "expected-nonce"));
    }

    private Jwt googleJwt(String audience, String nonce, boolean verified) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .subject("google-sub")
                .audience(List.of(audience))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("nonce", nonce)
                .claim("email", "user@gmail.com")
                .claim("email_verified", verified)
                .claim("name", "Google User")
                .build();
    }

    @Test
    void googleOnlySignupCreatesUserWithoutSamPasswordAndMatchingProfile() {
        String ticket = "one-time-ticket";
        UUID userId = UUID.randomUUID();
        var pending = new GoogleOAuthStateStore.PendingIdentity(
                "https://accounts.google.com", "google-sub", "user@gmail.com", "Google User", true);
        when(states.takePending(ticket)).thenReturn(Optional.of(pending));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });
        when(auth.createSession(userId, request)).thenReturn(AuthResponse.builder().userId(userId).build());

        AuthResponse response = service.completeRegistration(ticket, UserRole.CLIENT, request);

        assertEquals(userId, response.userId());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertEquals("user@gmail.com", saved.getValue().getEmail());
        assertEquals(UserRole.CLIENT, saved.getValue().getRole());
        assertEquals(null, saved.getValue().getPasswordHash());
        verify(clients).save(any());
        verify(freelancers, never()).save(any());
        verify(identities).saveAndFlush(any(GoogleIdentity.class));
    }

    @Test
    void linkingRequiresSamPasswordBeforeConsumingGoogleIdentity() {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).email("user@gmail.com").passwordHash("hash").isActive(true).build()));
        when(passwords.matches("wrong", "hash")).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.linkPending("ticket", userId, "wrong"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, error.getErrorCode());
        verify(states, never()).takePending("ticket");
    }
}
