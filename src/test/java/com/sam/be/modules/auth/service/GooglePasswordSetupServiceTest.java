package com.sam.be.modules.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.auth.config.GoogleOAuthProperties;
import com.sam.be.modules.auth.entity.GoogleIdentity;
import com.sam.be.modules.auth.repository.GoogleIdentityRepository;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.ClientProfileRepository;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestClient;

class GooglePasswordSetupServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final User user = User.builder().id(userId).email("user@gmail.com").build();
    private final GoogleOAuthStateStore states = mock(GoogleOAuthStateStore.class);
    private final GoogleIdentityRepository identities = mock(GoogleIdentityRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private GoogleOAuthService service;

    @BeforeEach
    void setUp() {
        var properties = new GoogleOAuthProperties();
        properties.setClientId("sam-client");
        properties.setClientSecret("test-secret");
        properties.setRedirectUri("https://api.sam.pro.vn/api/v1/auth/google/callback");
        properties.setFrontendOrigin("https://www.sam.pro.vn");
        service =
                new GoogleOAuthService(
                        properties,
                        states,
                        identities,
                        users,
                        mock(ClientProfileRepository.class),
                        mock(FreelancerProfileRepository.class),
                        mock(AuthService.class),
                        encoder,
                        mock(RestClient.class),
                        new ObjectMapper());
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.findByIdForPasswordSetup(userId)).thenReturn(Optional.of(user));
        when(identities.existsByUserId(userId)).thenReturn(true);
    }

    @Test
    void startBindsOAuthAttemptToTheAuthenticatedUserWithPkce() {
        var start = service.startPasswordSetup(userId);
        var attempt = ArgumentCaptor.forClass(GoogleOAuthStateStore.Attempt.class);
        verify(states).saveAttempt(eq(start.state()), attempt.capture());
        assertEquals("PASSWORD_SETUP", attempt.getValue().intent());
        assertEquals(userId.toString(), attempt.getValue().userId());
        assertTrue(start.url().contains("code_challenge_method=S256"));
        assertFalse(start.url().contains(attempt.getValue().verifier()));
    }

    @Test
    void onlyThePreviouslyLinkedGoogleIdentityCanIssueASetupProof() {
        var linked = GoogleIdentity.builder().user(user).subject("original-subject").build();
        var callback = service.confirmPasswordSetup(userId, Optional.of(linked));
        assertEquals(GoogleOAuthService.Outcome.PASSWORD_SETUP_VERIFIED, callback.outcome());
        assertNull(callback.auth());
        verify(states).savePasswordSetupGrant(callback.pendingTicket(), userId);
    }

    @Test
    void unlinkedOrAnotherUsersGoogleIdentityCannotIssueAProof() {
        // Even an identical email is insufficient; the issuer/subject link must resolve to this
        // user.
        var another = User.builder().id(UUID.randomUUID()).email(user.getEmail()).build();
        var linked = GoogleIdentity.builder().user(another).build();
        assertEquals(
                ErrorCode.FORBIDDEN_ACTION,
                assertThrows(
                                ApiException.class,
                                () -> service.confirmPasswordSetup(userId, Optional.of(linked)))
                        .getErrorCode());
        assertThrows(
                ApiException.class, () -> service.confirmPasswordSetup(userId, Optional.empty()));
        verifyNoInteractions(states);
    }

    @Test
    void expiredOrMissingProofCannotCreateAPassword() {
        for (String ticket : new String[] {null, "expired"}) {
            when(states.takePasswordSetupGrant(ticket)).thenReturn(Optional.empty());
            assertEquals(
                    ErrorCode.REQUEST_FAILED,
                    assertThrows(
                                    ApiException.class,
                                    () ->
                                            service.setupPassword(
                                                    userId, ticket, "new-password", "new-password"))
                            .getErrorCode());
        }
        assertNull(user.getPasswordHash());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void proofForAnotherUserIsRejected() {
        when(states.takePasswordSetupGrant("other-ticket"))
                .thenReturn(
                        Optional.of(
                                new GoogleOAuthStateStore.PasswordSetupGrant(UUID.randomUUID())));
        assertEquals(
                ErrorCode.FORBIDDEN_ACTION,
                assertThrows(
                                ApiException.class,
                                () ->
                                        service.setupPassword(
                                                userId,
                                                "other-ticket",
                                                "new-password",
                                                "new-password"))
                        .getErrorCode());
        assertNull(user.getPasswordHash());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void passwordIsHashedWithoutTrimmingAndCannotBeOverwritten() {
        when(states.takePasswordSetupGrant("proof"))
                .thenReturn(Optional.of(new GoogleOAuthStateStore.PasswordSetupGrant(userId)));
        service.setupPassword(userId, "proof", " new-password ", " new-password ");
        assertTrue(encoder.matches(" new-password ", user.getPasswordHash()));
        assertFalse(encoder.matches("new-password", user.getPasswordHash()));
        verify(users).findByIdForPasswordSetup(userId);
        verify(users).saveAndFlush(user);
        String originalHash = user.getPasswordHash();
        assertEquals(
                ErrorCode.REQUEST_FAILED,
                assertThrows(
                                ApiException.class,
                                () ->
                                        service.setupPassword(
                                                userId, "proof", "replacement", "replacement"))
                        .getErrorCode());
        assertEquals(originalHash, user.getPasswordHash());
        verify(states, times(1)).takePasswordSetupGrant("proof");
    }

    @Test
    void invalidPasswordDoesNotConsumeTheProof() {
        for (String password : new String[] {"short", "        ", "a".repeat(73), "ấ".repeat(25)}) {
            assertEquals(
                    ErrorCode.VALIDATION_ERROR,
                    assertThrows(
                                    ApiException.class,
                                    () ->
                                            service.setupPassword(
                                                    userId, "proof", password, password))
                            .getErrorCode());
        }
        assertThrows(
                ApiException.class,
                () -> service.setupPassword(userId, "proof", "new-password", "different"));
        verifyNoInteractions(states);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void acceptsTheExactBcryptUtf8Limit() {
        when(states.takePasswordSetupGrant("proof"))
                .thenReturn(Optional.of(new GoogleOAuthStateStore.PasswordSetupGrant(userId)));
        String password = "ấ".repeat(24); // 72 UTF-8 bytes
        service.setupPassword(userId, "proof", password, password);
        assertTrue(encoder.matches(password, user.getPasswordHash()));
    }

    @Test
    void disabledOrNonGoogleUsersCannotEnroll() {
        user.setIsActive(false);
        assertThrows(ApiException.class, () -> service.startPasswordSetup(userId));
        assertThrows(
                ApiException.class,
                () -> service.setupPassword(userId, "proof", "new-password", "new-password"));
        user.setIsActive(true);
        when(identities.existsByUserId(userId)).thenReturn(false);
        assertThrows(ApiException.class, () -> service.startPasswordSetup(userId));
        assertThrows(
                ApiException.class,
                () -> service.setupPassword(userId, "proof", "new-password", "new-password"));
        verifyNoInteractions(states);
    }

    @Test
    void statusRequiresAnUnexpiredProofForTheSameUser() {
        when(states.getPasswordSetupGrant("proof"))
                .thenReturn(Optional.of(new GoogleOAuthStateStore.PasswordSetupGrant(userId)));
        assertTrue(service.passwordSetupStatus(userId, "proof").googleConfirmed());
        assertFalse(service.passwordSetupStatus(userId, null).googleConfirmed());
        when(states.getPasswordSetupGrant("other"))
                .thenReturn(
                        Optional.of(
                                new GoogleOAuthStateStore.PasswordSetupGrant(UUID.randomUUID())));
        assertFalse(service.passwordSetupStatus(userId, "other").googleConfirmed());
        user.setPasswordHash("existing-hash");
        var status = service.passwordSetupStatus(userId, "proof");
        assertTrue(status.hasPassword());
        assertFalse(status.canSetup());
        assertFalse(status.googleConfirmed());
        assertThrows(ApiException.class, () -> service.startPasswordSetup(userId));
    }
}
