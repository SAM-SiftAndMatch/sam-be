package com.sam.be.modules.payment.controller;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sam.be.common.response.ApiResponse;
import com.sam.be.modules.finance.FinanceStore;
import com.sam.be.modules.finance.Pay2sClient;
import com.sam.be.modules.payment.service.Pay2sIpnService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Local-only helper: feeds a signed fake Pay2S IPN through the real inbox/fulfillment pipeline.
 * Never active outside the dev profile, and disabled by default.
 */
@RestController
@RequestMapping("/api/v1/payments/demo")
@Profile("dev")
@ConditionalOnProperty(name = "payments.demo-simulation-enabled", havingValue = "true")
@RequiredArgsConstructor
public class PaymentDemoController {

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

    private final Pay2sIpnService ipnService;
    private final FinanceStore store;

    @Value("${payments.environment:SANDBOX}")
    private String environment;

    @Value("${pay2s.access-key:}")
    private String accessKey;

    @Value("${pay2s.partner-code:}")
    private String partnerCode;

    @Value("${pay2s.secret-key:}")
    private String secretKey;

    @PostMapping("/{checkoutId}/simulate-paid")
    public ApiResponse<Map<String, Object>> simulatePaid(
            @PathVariable UUID checkoutId, @AuthenticationPrincipal Jwt jwt) {
        if (!"SANDBOX".equalsIgnoreCase(environment))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Chỉ mô phỏng thanh toán ở môi trường SANDBOX");
        if (jwt == null || jwt.getClaimAsString("user_id") == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập");

        var rows =
                store.rows(
                        "SELECT id,owner_id,status,amount,transfer_content,expires_at FROM payment_checkouts WHERE id=?",
                        checkoutId);
        if (rows.isEmpty())
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Không tìm thấy đơn thanh toán");
        var row = rows.getFirst();
        UUID owner = UUID.fromString(jwt.getClaimAsString("user_id"));
        if (!owner.equals(FinanceStore.id(row, "ownerId")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Đơn thanh toán thuộc người dùng khác");
        if (!Set.of("CREATING", "PENDING", "UNKNOWN").contains(row.get("status").toString()))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Đơn không còn ở trạng thái có thể thanh toán");
        if (FinanceStore.instant(row, "expiresAt").isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn thanh toán đã hết hạn");

        String orderId = checkoutId.toString().replace("-", "");
        String transId = "DEMO" + UUID.randomUUID().toString().replace("-", "");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("accessKey", accessKey);
        fields.put(
                "amount",
                FinanceStore.money(row, "amount").stripTrailingZeros().toPlainString());
        fields.put("extraData", "");
        fields.put("message", "Local demo payment");
        fields.put("orderId", orderId);
        fields.put("orderInfo", row.get("transferContent").toString());
        fields.put("orderType", "COLLECTION");
        fields.put("partnerCode", partnerCode);
        fields.put("payType", "QR");
        fields.put("requestId", UUID.randomUUID().toString());
        fields.put("responseTime", String.valueOf(Instant.now().toEpochMilli()));
        fields.put("resultCode", "0");
        fields.put("transId", transId);

        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        List<String> parts = new ArrayList<>();
        for (String field : IPN_FIELDS) {
            payload.put(field, fields.get(field));
            parts.add(field + "=" + fields.get(field));
        }
        payload.put("m2signature", Pay2sClient.sign(secretKey, String.join("&", parts)));

        ipnService.accept(payload);
        return ApiResponse.<Map<String, Object>>builder()
                .message("Đã mô phỏng thanh toán thành công")
                .result(
                        Map.of(
                                "orderId",
                                checkoutId.toString(),
                                "providerReference",
                                transId))
                .build();
    }
}
