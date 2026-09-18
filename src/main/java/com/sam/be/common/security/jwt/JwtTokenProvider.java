package com.sam.be.common.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class JwtTokenProvider {

    JwtProperties jwtProperties;

    public String generateAccessToken(
            UUID userId, String email, UUID sessionId, String accountType) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSignerKeyBase64());
            JWSSigner signer = new MACSigner(keyBytes);

            Instant now = Instant.now();
            Instant exp = now.plusSeconds(jwtProperties.getAccessExpiration());

            JWTClaimsSet claimsSet =
                    new JWTClaimsSet.Builder()
                            .subject(email)
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
            log.error("Error generating access token: ", e);
            throw new RuntimeException("Cannot generate access token", e);
        }
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }
}
