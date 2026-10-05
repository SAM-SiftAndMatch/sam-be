package com.sam.be.infrastructure.thirdparty.vnpay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vnpay")
@Getter
@Setter
public class VnpayProperties {

    private String tmnCode;

    private String hashSecret;

    private String apiUrl;

    private String apiQueryUrl;

    private String returnUrl;

    private String ipnUrl;

    private String defaultClientIp;
}
