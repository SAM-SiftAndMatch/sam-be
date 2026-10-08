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
        String candidate = url.trim();
        if (allowedReturnUrls.contains(candidate)) {
            return true;
        }
        // Nới theo origin (scheme + host + port): các màn mới (vd: /workspace/{id}/contract)
        // vẫn pass miễn cùng frontend đã whitelist, không mở sang domain lạ.
        String candidateOrigin = originOf(candidate);
        if (candidateOrigin == null) {
            return false;
        }
        return allowedReturnUrls.stream()
                .map(this::originOf)
                .anyMatch(candidateOrigin::equals);
    }

    private String originOf(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase() + port;
        } catch (Exception e) {
            return null;
        }
    }

    private String defaultClientIp;
}
