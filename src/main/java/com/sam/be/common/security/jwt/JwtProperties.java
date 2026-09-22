package com.sam.be.common.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    private String signerKeyBase64;
    private long accessExpiration = 900; // 15 phút (giây)
    private long refreshExpiration = 604800; // 7 ngày (giây)
    private String issuer = "sam-be";
}
