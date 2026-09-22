package com.sam.be.common.security.jwt;

import jakarta.annotation.PostConstruct;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class CustomJwtDecoder implements JwtDecoder {

    JwtProperties jwtProperties;

    @NonFinal NimbusJwtDecoder nimbusJwtDecoder;

    @PostConstruct
    public void init() {
        if (jwtProperties.getSignerKeyBase64() != null
                && !jwtProperties.getSignerKeyBase64().isBlank()) {
            byte[] signerKeyBytes = Base64.getDecoder().decode(jwtProperties.getSignerKeyBase64());
            SecretKeySpec secretKeySpec = new SecretKeySpec(signerKeyBytes, "HmacSHA512");

            nimbusJwtDecoder =
                    NimbusJwtDecoder.withSecretKey(secretKeySpec)
                            .macAlgorithm(MacAlgorithm.HS512)
                            .build();
        }
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        if (nimbusJwtDecoder == null) {
            init();
            if (nimbusJwtDecoder == null) {
                throw new JwtException("JWT signer key is not configured");
            }
        }

        // NimbusJwtDecoder thực hiện xác thực chữ ký HMAC-SHA512, kiểm tra expiration (exp)
        // và parse toàn bộ JWT claims theo chuẩn RFC 7519 mà không cần giải mã 2 lần.
        return nimbusJwtDecoder.decode(token);
    }
}
