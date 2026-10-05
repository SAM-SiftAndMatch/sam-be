package com.sam.be.infrastructure.thirdparty.vnpay;

import java.util.List;
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

    private List<String> allowedReturnUrls;

    private String ipnUrl;

    public String getDefaultReturnUrl() {
        return (allowedReturnUrls != null && !allowedReturnUrls.isEmpty())
                ? allowedReturnUrls.get(0)
                : null;
    }

    public boolean isReturnUrlAllowed(String url) {
        if (url == null || url.isBlank() || allowedReturnUrls == null) {
            return false;
        }
        return allowedReturnUrls.contains(url.trim());
    }

    private String defaultClientIp;
}
