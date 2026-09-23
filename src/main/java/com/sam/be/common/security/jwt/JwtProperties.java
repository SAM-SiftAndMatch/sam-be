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
    private long accessExpiration;
    private long refreshExpiration;
    private String issuer;
    private boolean cookieSecure;
}
