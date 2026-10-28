package com.sam.be.modules.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sam.be.common.constant.enums.UserRole;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.auth.config.GoogleOAuthProperties;
import com.sam.be.modules.auth.dto.response.AuthResponse;
import com.sam.be.modules.auth.entity.GoogleIdentity;
import com.sam.be.modules.auth.repository.GoogleIdentityRepository;
import com.sam.be.modules.user.entity.ClientProfile;
import com.sam.be.modules.user.entity.FreelancerProfile;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.ClientProfileRepository;
import com.sam.be.modules.user.repository.FreelancerProfileRepository;
import com.sam.be.modules.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoogleOAuthProperties properties;
    private final GoogleOAuthStateStore stateStore;
    private final GoogleIdentityRepository identities;
    private final UserRepository users;
    private final ClientProfileRepository clients;
    private final FreelancerProfileRepository freelancers;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final RestClient restClient;
    private final ObjectMapper mapper;

    private final NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URL).build();

    public record Start(String state, String url) {}

    public enum Outcome { SIGNED_IN, SIGN_UP, LINK_REQUIRED, LINKED }

    public record Callback(Outcome outcome, AuthResponse auth, String pendingTicket) {}

    public Start startLogin() {
        return start("LOGIN", null);
    }

    public boolean isLinked(UUID userId) {
        return identities.existsByUserId(userId);
    }

    public Start startLink(UUID userId, String password) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (password == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (identities.existsByUserId(userId)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Google is already linked");
        }
        return start("LINK", userId);
    }

    private Start start(String intent, UUID userId) {
        requireConfigured();
        String state = randomToken();
        String nonce = randomToken();
        String verifier = randomToken();
        String challenge = base64Url(sha256(verifier));
        stateStore.saveAttempt(
                state,
                new GoogleOAuthStateStore.Attempt(
                        verifier, nonce, intent, userId == null ? null : userId.toString()));

        String url =
                UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", properties.getClientId())
                        .queryParam("redirect_uri", properties.getRedirectUri())
                        .queryParam("scope", "openid email profile")
                        .queryParam("state", state)
                        .queryParam("nonce", nonce)
                        .queryParam("code_challenge", challenge)
                        .queryParam("code_challenge_method", "S256")
                        .build()
                        .encode()
                        .toUriString();
        return new Start(state, url);
    }

    public Callback callback(
            String code, String state, String browserState, HttpServletRequest request) {
        requireConfigured();
        if (code == null
                || code.isBlank()
                || state == null
                || browserState == null
                || !constantTimeEquals(state, browserState)) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        GoogleOAuthStateStore.Attempt attempt =
                stateStore
                        .takeAttempt(state)
                        .orElseThrow(() -> new ApiException(ErrorCode.GOOGLE_AUTH_FAILED));
        GoogleOAuthStateStore.PendingIdentity googleIdentity =
                verifyIdentity(exchangeCode(code, attempt.verifier()), attempt.nonce());
        Optional<GoogleIdentity> linked =
                identities.findByIssuerAndSubject(googleIdentity.issuer(), googleIdentity.subject());

        if ("LINK".equals(attempt.intent())) {
            if (attempt.userId() == null) {
                throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
            }
            UUID userId = UUID.fromString(attempt.userId());
            linkToUser(userId, googleIdentity);
            return new Callback(Outcome.LINKED, null, null);
        }

        if (linked.isPresent()) {
            AuthResponse auth = authService.createSession(linked.get().getUser().getId(), request);
            return new Callback(Outcome.SIGNED_IN, auth, null);
        }

        Outcome outcome =
                users.existsByEmail(googleIdentity.email())
                        ? Outcome.LINK_REQUIRED
                        : Outcome.SIGN_UP;
        if (outcome == Outcome.SIGN_UP && !googleIdentity.authoritativeEmail()) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        String ticket = randomToken();
        stateStore.savePending(ticket, googleIdentity);
        return new Callback(outcome, null, ticket);
    }

    private String exchangeCode(String code, String verifier) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("grant_type", "authorization_code");
        form.add("code_verifier", verifier);
        try {
            String body =
                    restClient
                            .post()
                            .uri(TOKEN_URL)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(String.class);
            JsonNode response = mapper.readTree(body);
            String idToken = response.path("id_token").asText();
            if (idToken.isBlank()) {
                throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
            }
            return idToken;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    private GoogleOAuthStateStore.PendingIdentity verifyIdentity(String idToken, String nonce) {
        try {
            return validateIdentity(decoder.decode(idToken), nonce);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    GoogleOAuthStateStore.PendingIdentity validateIdentity(Jwt jwt, String nonce) {
        String issuer = jwt.getClaimAsString("iss");
        if (!(GOOGLE_ISSUER.equals(issuer) || "accounts.google.com".equals(issuer))
                || !jwt.getAudience().contains(properties.getClientId())
                || (jwt.getClaimAsString("azp") != null
                        && !properties.getClientId().equals(jwt.getClaimAsString("azp")))
                || jwt.getExpiresAt() == null
                || !jwt.getExpiresAt().isAfter(Instant.now())
                || !constantTimeEquals(nonce, jwt.getClaimAsString("nonce"))
                || !Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null
                || subject.isBlank()
                || email == null
                || email.isBlank()
                || !email.contains("@")) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        email = email.trim().toLowerCase(Locale.ROOT);
        int at = email.indexOf('@');
        if (at < 1 || at == email.length() - 1 || email.length() > 255 || subject.length() > 255) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = email.substring(0, at);
        }
        String hostedDomain = jwt.getClaimAsString("hd");
        boolean authoritative =
                email.endsWith("@gmail.com")
                        || (hostedDomain != null && !hostedDomain.isBlank());
        String cleanName = name.strip();
        return new GoogleOAuthStateStore.PendingIdentity(
                GOOGLE_ISSUER,
                subject,
                email,
                cleanName.substring(0, Math.min(255, cleanName.length())),
                authoritative);
    }

    @Transactional
    public AuthResponse completeRegistration(
            String ticket, UserRole role, HttpServletRequest request) {
        if (role != UserRole.CLIENT && role != UserRole.FREELANCER) {
            throw new ApiException(ErrorCode.FORBIDDEN_ACTION);
        }
        GoogleOAuthStateStore.PendingIdentity pending = takePending(ticket);
        if (!pending.authoritativeEmail()) {
            // Google may not remain authoritative for arbitrary third-party mailboxes.
            throw new ApiException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
        if (users.existsByEmail(pending.email())
                || identities.findByIssuerAndSubject(pending.issuer(), pending.subject()).isPresent()) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user =
                users.saveAndFlush(
                        User.builder()
                                .email(pending.email())
                                .fullName(pending.name())
                                .role(role)
                                .isActive(true)
                                .build());
        if (role == UserRole.CLIENT) {
            clients.save(ClientProfile.builder().user(user).build());
        } else {
            freelancers.save(FreelancerProfile.builder().user(user).build());
        }
        saveIdentity(user, pending);
        return authService.createSession(user.getId(), request);
    }

    @Transactional
    public void linkPending(String ticket, UUID userId, String password) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getPasswordHash() == null
                || password == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        GoogleOAuthStateStore.PendingIdentity pending = takePending(ticket);
        if (!user.getEmail().equalsIgnoreCase(pending.email())) {
            throw new ApiException(ErrorCode.GOOGLE_LINK_REQUIRED);
        }
        linkToUser(userId, pending);
    }

    @Transactional
    protected void linkToUser(UUID userId, GoogleOAuthStateStore.PendingIdentity pending) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (identities.existsByUserId(userId)
                || identities.findByIssuerAndSubject(pending.issuer(), pending.subject()).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE);
        }
        saveIdentity(user, pending);
    }

    private void saveIdentity(User user, GoogleOAuthStateStore.PendingIdentity pending) {
        identities.saveAndFlush(
                GoogleIdentity.builder()
                        .user(user)
                        .issuer(pending.issuer())
                        .subject(pending.subject())
                        .emailAtLink(pending.email())
                        .build());
    }

    private GoogleOAuthStateStore.PendingIdentity takePending(String ticket) {
        return stateStore
                .takePending(ticket)
                .orElseThrow(() -> new ApiException(ErrorCode.GOOGLE_AUTH_FAILED));
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ApiException(ErrorCode.GOOGLE_AUTH_NOT_CONFIGURED);
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return base64Url(bytes);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return a != null
                && b != null
                && MessageDigest.isEqual(
                        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
