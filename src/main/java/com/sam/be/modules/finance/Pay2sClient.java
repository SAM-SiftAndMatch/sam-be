package com.sam.be.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Collection Link V1 and its distinct IPN signature; no invented disbursement endpoint. */
@Component
public class Pay2sClient {
    private final String provider,
            baseUrl,
            accessKey,
            secretKey,
            partnerCode,
            bankCode,
            bankAccount,
            ipnUrl,
            environment;
    private final RestClient http;
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public Pay2sClient(
            @Value("${payments.provider:vnpay}") String provider,
            @Value("${payments.environment:SANDBOX}") String environment,
            @Value("${pay2s.base-url:https://sandbox-payment.pay2s.vn}") String baseUrl,
            @Value("${pay2s.access-key:}") String accessKey,
            @Value("${pay2s.secret-key:}") String secretKey,
            @Value("${pay2s.partner-code:}") String partnerCode,
            @Value("${pay2s.bank-code:}") String bankCode,
            @Value("${pay2s.bank-account:}") String bankAccount,
            @Value("${pay2s.ipn-url:}") String ipnUrl) {
        this.provider = provider;
        this.environment = environment.toUpperCase(Locale.ROOT);
        this.baseUrl = baseUrl;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.partnerCode = partnerCode;
        this.bankCode = bankCode;
        this.bankAccount = bankAccount;
        this.ipnUrl = ipnUrl;
        if (!Set.of("vnpay", "pay2s").contains(provider)
                || !Set.of("LIVE", "SANDBOX").contains(this.environment))
            throw new IllegalStateException("Invalid payment provider/environment");
        if ("pay2s".equals(provider)) {
            if (List.of(accessKey, secretKey, partnerCode, bankCode, bankAccount, ipnUrl).stream()
                    .anyMatch(String::isBlank))
                throw new IllegalStateException("Pay2S configuration incomplete");
            if (!Set.of("https://payment.pay2s.vn", "https://sandbox-payment.pay2s.vn")
                    .contains(baseUrl))
                throw new IllegalStateException("Untrusted Pay2S API origin");
            if ("LIVE".equals(this.environment)
                    && (!baseUrl.equals("https://payment.pay2s.vn")
                            || !ipnUrl.startsWith("https://")))
                throw new IllegalStateException(
                        "Live Pay2S requires production origin and HTTPS IPN");
        }
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.NEVER)
                                .connectTimeout(Duration.ofSeconds(5))
                                .build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean enabled() {
        return "pay2s".equals(provider);
    }

    public String environment() {
        return environment;
    }

    public record Checkout(String url, boolean demo) {}

    public Checkout create(String order, long amount, String returnUrl) {
        if (!enabled()) throw FinanceStore.invalid("Pay2S chưa được bật");
        if ("LIVE".equals(environment) && !returnUrl.startsWith("https://"))
            throw FinanceStore.invalid("Thanh toán thật yêu cầu return URL HTTPS");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessKey", accessKey);
        body.put("amount", amount);
        body.put(
                "bankAccounts",
                List.of(Map.of("account_number", bankAccount, "bank_id", bankCode)));
        body.put("ipnUrl", ipnUrl);
        body.put("orderId", order);
        body.put("orderInfo", "SAM" + order.substring(0, 25));
        body.put("partnerCode", partnerCode);
        body.put("redirectUrl", returnUrl);
        body.put("requestId", order);
        body.put("requestType", "pay2s");
        String raw =
                "accessKey="
                        + accessKey
                        + "&amount="
                        + amount
                        + "&bankAccounts=Array&ipnUrl="
                        + ipnUrl
                        + "&orderId="
                        + order
                        + "&orderInfo="
                        + body.get("orderInfo")
                        + "&partnerCode="
                        + partnerCode
                        + "&redirectUrl="
                        + returnUrl
                        + "&requestId="
                        + order
                        + "&requestType=pay2s";
        body.put("signature", sign(secretKey, raw));
        try {
            String response =
                    http.post()
                            .uri(baseUrl + "/v1/gateway/api/create")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .body(JSON.writeValueAsString(body))
                            .retrieve()
                            .body(String.class);
            JsonNode result = JSON.readTree(response);
            if (result == null
                    || result.path("resultCode").asInt(-1) != 0
                    || !order.equals(result.path("orderId").asText())
                    || new java.math.BigDecimal(result.path("amount").asText())
                                    .compareTo(java.math.BigDecimal.valueOf(amount))
                            != 0) throw new IllegalStateException();
            String url = result.path("payUrl").asText();
            URI parsed = URI.create(url);
            if (!"https".equals(parsed.getScheme())
                    || !Set.of("payment.pay2s.vn", "sandbox-payment.pay2s.vn")
                            .contains(parsed.getHost())
                    || parsed.getUserInfo() != null
                    || parsed.getPort() != -1) throw new IllegalStateException();
            boolean demo =
                    result.path("isDemo").asBoolean(false)
                            || "demo".equalsIgnoreCase(result.path("environment").asText())
                            || "sandbox-payment.pay2s.vn".equals(parsed.getHost())
                            || !"LIVE".equals(environment);
            if ("LIVE".equals(environment) && demo)
                throw FinanceStore.invalid(
                        "Tài khoản Pay2S demo không được dùng để nhận tiền thật");
            return new Checkout(url, demo);
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Không tạo được thanh toán Pay2S; hãy thử lại hoặc liên hệ hỗ trợ");
        }
    }

    private static final List<String> IPN_FIELDS =
            List.of(
                    "accessKey",
                    "amount",
                    "extraData",
                    "message",
                    "orderId",
                    "orderInfo",
                    "orderType",
                    "partnerCode",
                    "payType",
                    "requestId",
                    "responseTime",
                    "resultCode",
                    "transId");

    public boolean verify(JsonNode body) {
        if (!enabled() || secretKey.isBlank() || !body.isObject()) return false;
        List<String> parts = new ArrayList<>();
        for (String field : IPN_FIELDS) {
            JsonNode value = body.get(field);
            if (value == null
                    || (!value.isTextual() && !value.isNumber())
                    || value.asText().length() > 2000) return false;
            parts.add(field + "=" + value.asText());
        }
        String signature = body.path("m2signature").asText();
        return signature.matches("[a-f0-9]{64}")
                && accessKey.equals(body.path("accessKey").asText())
                && partnerCode.equals(body.path("partnerCode").asText())
                && MessageDigest.isEqual(
                        sign(secretKey, String.join("&", parts))
                                .getBytes(StandardCharsets.US_ASCII),
                        signature.getBytes(StandardCharsets.US_ASCII));
    }

    public static String sign(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign Pay2S request");
        }
    }
}
