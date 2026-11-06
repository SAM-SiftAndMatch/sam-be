package com.sam.be.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.sam.be.common.exception.PaymentGatewayException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Collection Link V1 and its distinct IPN signature; no invented disbursement endpoint. */
@Component
@Slf4j
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
            @Value("${pay2s.base-url:https://payment.pay2s.vn}") String baseUrl,
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
        if ("LIVE".equals(environment) && (returnUrl == null || !returnUrl.startsWith("https://")))
            throw FinanceStore.invalid("Thanh toán thật yêu cầu return URL HTTPS");
        String reference = UUID.randomUUID().toString();
        CheckoutContext context =
                new CheckoutContext(reference, safeOrigin(ipnUrl), safeOrigin(returnUrl));
        URI ipn = publicHttpsCallback(ipnUrl);
        if (ipn == null) throw failure(context, "IPN_URL_INVALID", null, null, null);
        URI requestedReturn;
        if (returnUrl == null) throw failure(context, "REDIRECT_URL_INVALID", null, null, null);
        try {
            requestedReturn = URI.create(returnUrl);
        } catch (IllegalArgumentException ex) {
            throw failure(context, "REDIRECT_URL_INVALID", null, null, ex);
        }
        if (requestedReturn.getScheme() == null
                || !Set.of("http", "https").contains(requestedReturn.getScheme())
                || requestedReturn.getHost() == null
                || requestedReturn.getUserInfo() != null
                || requestedReturn.getRawQuery() != null
                || requestedReturn.getRawFragment() != null)
            throw failure(context, "REDIRECT_URL_INVALID", null, null, null);
        if (publicHttpsCallback(returnUrl) == null) {
            if ("LIVE".equals(environment))
                throw failure(context, "REDIRECT_URL_INVALID", null, null, null);
            // One public backend tunnel handles both callbacks; the browser can still use local
            // web.
            String path = ipn.getRawPath();
            String relayPath =
                    path.endsWith("/pay2s-ipn")
                            ? path.substring(0, path.length() - "pay2s-ipn".length())
                                    + "pay2s-return"
                            : "/api/v1/payments/pay2s-return";
            returnUrl = ipn.getScheme() + "://" + ipn.getRawAuthority() + relayPath;
        }
        final String checkoutReturn = returnUrl;
        context = new CheckoutContext(reference, safeOrigin(ipnUrl), safeOrigin(checkoutReturn));
        final CheckoutContext failureContext = context;
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
            return http.post()
                    .uri(baseUrl + "/v1/gateway/api/create")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(JSON.writeValueAsString(body))
                    .exchange(
                            (request, response) -> {
                                int status = response.getStatusCode().value();
                                byte[] bytes = response.getBody().readNBytes(65537);
                                if (bytes.length > 65536)
                                    throw failure(
                                            failureContext,
                                            "RESPONSE_TOO_LARGE",
                                            status,
                                            null,
                                            null);
                                JsonNode result;
                                try {
                                    result = JSON.readTree(bytes);
                                } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                                    throw failure(
                                            failureContext,
                                            status >= 200 && status < 300
                                                    ? "INVALID_RESPONSE"
                                                    : "HTTP_REJECTED",
                                            status,
                                            null,
                                            ex);
                                }
                                Integer code = providerCode(result);
                                if (status < 200 || status >= 300) {
                                    String detail = rejectionReason(result);
                                    String reason =
                                            status == 401 || status == 403
                                                    ? "ACCESS_DENIED"
                                                    : status == 429
                                                            ? "RATE_LIMITED"
                                                            : (status == 400 || status == 422)
                                                                            && !"PROVIDER_REJECTED"
                                                                                    .equals(detail)
                                                                    ? detail
                                                                    : "HTTP_REJECTED";
                                    throw failure(failureContext, reason, status, code, null);
                                }
                                if (result == null || !result.isObject() || code == null)
                                    throw failure(
                                            failureContext, "INVALID_RESPONSE", status, null, null);
                                if (code != 0)
                                    throw failure(
                                            failureContext,
                                            rejectionReason(result),
                                            status,
                                            code,
                                            null);
                                java.math.BigDecimal receivedAmount;
                                try {
                                    receivedAmount =
                                            new java.math.BigDecimal(
                                                    result.path("amount").asText());
                                } catch (NumberFormatException ex) {
                                    throw failure(
                                            failureContext, "INVALID_RESPONSE", status, code, ex);
                                }
                                if (!order.equals(result.path("orderId").asText())
                                        || receivedAmount.compareTo(
                                                        java.math.BigDecimal.valueOf(amount))
                                                != 0)
                                    throw failure(
                                            failureContext, "PAYMENT_MISMATCH", status, code, null);
                                String url = result.path("payUrl").asText();
                                URI parsed;
                                try {
                                    parsed = URI.create(url);
                                } catch (IllegalArgumentException ex) {
                                    throw failure(
                                            failureContext,
                                            "UNTRUSTED_CHECKOUT_URL",
                                            status,
                                            code,
                                            ex);
                                }
                                if (!"https".equals(parsed.getScheme())
                                        || parsed.getHost() == null
                                        || !Set.of("payment.pay2s.vn", "sandbox-payment.pay2s.vn")
                                                .contains(parsed.getHost())
                                        || parsed.getUserInfo() != null
                                        || parsed.getPort() != -1 && parsed.getPort() != 443)
                                    throw failure(
                                            failureContext,
                                            "UNTRUSTED_CHECKOUT_URL",
                                            status,
                                            code,
                                            null);
                                boolean declaredLive =
                                        Set.of("live", "production")
                                                .contains(
                                                        result.path("environment")
                                                                .asText()
                                                                .toLowerCase(Locale.ROOT));
                                boolean providerDemo =
                                        result.path("isDemo").asBoolean(false)
                                                || Set.of("demo", "sandbox", "test")
                                                        .contains(
                                                                result.path("environment")
                                                                        .asText()
                                                                        .toLowerCase(Locale.ROOT))
                                                || "sandbox-payment.pay2s.vn"
                                                        .equals(parsed.getHost());
                                if ("LIVE".equals(environment) && providerDemo
                                        || "SANDBOX".equals(environment) && declaredLive)
                                    throw failure(
                                            failureContext,
                                            "ENVIRONMENT_MISMATCH",
                                            status,
                                            code,
                                            null);
                                return new Checkout(
                                        url, providerDemo || !"LIVE".equals(environment));
                            });
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            Throwable cause = ex;
            String reason = "CONNECTION_FAILED";
            for (int i = 0; i < 12 && cause != null; i++, cause = cause.getCause()) {
                if (cause instanceof java.net.UnknownHostException) {
                    reason = "DNS_FAILED";
                    break;
                }
                if (cause instanceof javax.net.ssl.SSLException) {
                    reason = "TLS_FAILED";
                    break;
                }
                if (cause instanceof java.net.SocketTimeoutException
                        || cause instanceof java.net.http.HttpTimeoutException) {
                    reason = "TIMEOUT";
                    break;
                }
            }
            throw failure(failureContext, reason, null, null, ex);
        } catch (Exception ex) {
            throw failure(failureContext, "INVALID_RESPONSE", null, null, ex);
        }
    }

    private record CheckoutContext(String reference, String ipnOrigin, String redirectOrigin) {}

    private static URI publicHttpsCallback(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme())
                    || host == null
                    || uri.getUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("localhost")
                    || host.endsWith(".localhost")
                    || host.endsWith(".local")
                    || host.equals("[::1]")
                    || host.equals("::1")
                    || host.equals("0.0.0.0")
                    || host.startsWith("127.")) return null;
            if (host.matches("(?:10|192\\.168|172\\.(?:1[6-9]|2[0-9]|3[01]))\\..*")) return null;
            return uri;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String safeOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || !Set.of("http", "https").contains(uri.getScheme()))
                return "invalid";
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null)
                    .toASCIIString();
        } catch (Exception ex) {
            return "invalid";
        }
    }

    private static Integer providerCode(JsonNode result) {
        if (result == null || !result.isObject()) return null;
        JsonNode value = result.get("resultCode");
        if (value == null
                || (!value.isIntegralNumber() && !value.isTextual())
                || !value.asText().matches("-?[0-9]{1,6}")) return null;
        return Integer.valueOf(value.asText());
    }

    /**
     * Classify vendor text; never expose or log the text, which may echo credentials or account
     * data.
     */
    private static String rejectionReason(JsonNode result) {
        if (result == null || !result.isObject()) return "PROVIDER_REJECTED";
        StringBuilder text = new StringBuilder();
        for (JsonNode value :
                List.of(
                        result.path("message"),
                        result.path("error"),
                        result.path("error").path("message"),
                        result.path("error_description"))) {
            if (value.isTextual()) text.append(' ').append(value.asText());
        }
        String message =
                java.text.Normalizer.normalize(text.toString(), java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "")
                        .toLowerCase(Locale.ROOT)
                        .replace('đ', 'd');
        if (message.contains("signature")
                || message.contains("chu ky")
                || message.contains("secret key")
                || message.contains("secretkey")) return "SIGNATURE_REJECTED";
        if (message.contains("access")
                || message.contains("partner")
                || message.contains("credential")) return "ACCESS_DENIED";
        if (message.contains("ipn") && message.contains("redirect")) return "CALLBACK_URL_REJECTED";
        if (message.contains("ipn")) return "IPN_URL_REJECTED";
        if (message.contains("redirect")) return "REDIRECT_URL_REJECTED";
        if (message.contains("callback")) return "CALLBACK_URL_REJECTED";
        if (message.contains("bank")
                || message.contains("tai khoan")
                || message.contains("account_number")) return "BANK_ACCOUNT_REJECTED";
        if (message.contains("amount") || message.contains("so tien")) return "AMOUNT_REJECTED";
        return "PROVIDER_REJECTED";
    }

    private PaymentGatewayException failure(
            CheckoutContext context,
            String reason,
            Integer upstreamStatus,
            Integer resultCode,
            Exception exception) {
        // Do not log the exception/its message: HTTP clients and vendor JSON may contain secrets.
        log.warn(
                "PAY2S_CHECKOUT_FAILED reference={} reason={} upstreamHttpStatus={} upstreamResultCode={} environment={} apiOrigin={} ipnOrigin={} redirectOrigin={} exceptionType={}",
                context.reference(),
                reason,
                upstreamStatus,
                resultCode,
                environment,
                baseUrl,
                context.ipnOrigin(),
                context.redirectOrigin(),
                exception == null ? "none" : exception.getClass().getSimpleName());
        HttpStatus status =
                "TIMEOUT".equals(reason)
                        ? HttpStatus.GATEWAY_TIMEOUT
                        : "ENVIRONMENT_MISMATCH".equals(reason)
                                ? HttpStatus.CONFLICT
                                : HttpStatus.BAD_GATEWAY;
        String message =
                switch (reason) {
                    case "TIMEOUT" -> "Pay2S chưa phản hồi kịp. Vui lòng thử lại sau.";
                    case "CONNECTION_FAILED", "DNS_FAILED", "TLS_FAILED" ->
                            "Không kết nối được cổng thanh toán Pay2S. Vui lòng thử lại sau.";
                    case "RATE_LIMITED" ->
                            "Cổng thanh toán đang nhận quá nhiều yêu cầu. Vui lòng thử lại sau.";
                    case "ACCESS_DENIED",
                            "SIGNATURE_REJECTED",
                            "BANK_ACCOUNT_REJECTED",
                            "CALLBACK_URL_REJECTED",
                            "IPN_URL_REJECTED",
                            "REDIRECT_URL_REJECTED",
                            "IPN_URL_INVALID",
                            "REDIRECT_URL_INVALID",
                            "ENVIRONMENT_MISMATCH" ->
                            "Cổng thanh toán chưa sẵn sàng cho giao dịch này. Vui lòng liên hệ hỗ trợ.";
                    case "AMOUNT_REJECTED" ->
                            "Pay2S chưa chấp nhận số tiền thanh toán. Vui lòng liên hệ hỗ trợ.";
                    default -> "Chưa tạo được liên kết thanh toán Pay2S. Vui lòng liên hệ hỗ trợ.";
                };
        return new PaymentGatewayException(
                status,
                reason,
                context.reference(),
                upstreamStatus,
                resultCode,
                message,
                Map.of(
                        "ipnOrigin",
                        context.ipnOrigin(),
                        "redirectOrigin",
                        context.redirectOrigin()));
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
