package com.sam.be.common.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class JwtTokenProvider {

    JwtProperties jwtProperties;

    @NonFinal JWSSigner signer;

    @PostConstruct
    public void init() {
        if (jwtProperties.getSignerKeyBase64() != null
                && !jwtProperties.getSignerKeyBase64().isBlank()) {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSignerKeyBase64());
                signer = new MACSigner(keyBytes);
            } catch (Exception e) {
                log.error("Failed to initialize JWT signer: ", e);
                throw new IllegalStateException(
                        "Cannot initialize JWT signer with provided key", e);
            }
        }
    }

    public String generateAccessToken(
            UUID userId, String email, UUID sessionId, String accountType) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        if (signer == null) {
            init();
            if (signer == null) {
                throw new IllegalStateException("JWT signer key is not configured");
            }
        }

        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(jwtProperties.getAccessExpiration());

            JWTClaimsSet claimsSet =
                    new JWTClaimsSet.Builder()
                            .subject(email)
                            .issuer(jwtProperties.getIssuer())
                            .claim("user_id", userId.toString())
                            .claim("session_id", sessionId.toString())
                            .claim("account_type", accountType)
                            .issueTime(Date.from(now))
                            .expirationTime(Date.from(exp))
                            .jwtID(UUID.randomUUID().toString())
                            .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claimsSet);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (Exception e) {
            log.error("Error generating access token for userId={}: ", userId, e);
            throw new RuntimeException("Cannot generate access token", e);
        }
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }
}
