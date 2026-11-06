package com.sam.be.modules.finance;

import com.sam.be.infrastructure.thirdparty.vnpay.VnpayProperties;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentReturnPolicy {
    private final Set<String> origins = new HashSet<>(), paths = new HashSet<>();
    private final String fallback;
    private final boolean live;

    public PaymentReturnPolicy(
            VnpayProperties legacy,
            @Value("${payments.return-url:}") String fallback,
            @Value("${payments.allowed-return-origins:}") String configured,
            @Value("${payments.environment:SANDBOX}") String environment) {
        this.live = "LIVE".equalsIgnoreCase(environment);
        this.fallback = fallback.isBlank() ? legacy.getDefaultReturnUrl() : fallback;
        if (legacy.getAllowedReturnUrls() != null)
            for (String url : legacy.getAllowedReturnUrls()) {
                var parsed = parse(url);
                if (configured.isBlank()) origins.add(origin(parsed));
                paths.add(parsed.getRawPath());
            }
        for (String url : configured.split(","))
            if (!url.isBlank()) origins.add(origin(parse(url.trim())));
        if (this.fallback != null) {
            var parsed = parse(this.fallback);
            if (configured.isBlank()) origins.add(origin(parsed));
            paths.add(parsed.getRawPath());
        }
    }

    private static URI parse(String value) {
        try {
            URI uri = URI.create(value);
            if (!Set.of("https", "http").contains(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || uri.getRawQuery() != null) throw new IllegalArgumentException();
            return uri;
        } catch (Exception ex) {
            throw FinanceStore.invalid("Return URL không hợp lệ");
        }
    }

    private static String origin(URI uri) {
        int port = uri.getPort();
        return uri.getScheme()
                + "://"
                + uri.getHost().toLowerCase(Locale.ROOT)
                + ((port == -1
                                || port == 443 && uri.getScheme().equals("https")
                                || port == 80 && uri.getScheme().equals("http"))
                        ? ""
                        : ":" + port);
    }

    public String resolve(String requested) {
        String target = requested == null || requested.isBlank() ? fallback : requested.trim();
        if (target == null)
            throw FinanceStore.invalid("Chưa cấu hình trang quay lại sau thanh toán");
        URI uri = parse(target);
        boolean pathAllowed =
                paths.contains(uri.getRawPath())
                        || uri.getRawPath().matches("/workspace/[0-9a-fA-F-]{36}/contract");
        if (live && !"https".equals(uri.getScheme())
                || !origins.contains(origin(uri))
                || !pathAllowed) throw FinanceStore.invalid("Return URL chưa được cho phép");
        return target;
    }
}
