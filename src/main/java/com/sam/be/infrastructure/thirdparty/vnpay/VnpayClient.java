package com.sam.be.infrastructure.thirdparty.vnpay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VnpayClient {

    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATETIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnpayProperties properties;

    private final VnpaySigner signer;

    /**
     * Dựng URL thanh toán VNPay Sandbox.
     *
     * @param txnRef mã tham chiếu duy nhất (paymentId bỏ dấu gạch)
     * @param amountVnd số tiền VND (đơn vị đồng, BE tự nhân 100 theo chuẩn VNPay)
     */
    public String buildPaymentUrl(
            String txnRef, long amountVnd, String orderInfo, String clientIp) {
        return buildPaymentUrl(txnRef, amountVnd, orderInfo, clientIp, null);
    }

    public String buildPaymentUrl(
            String txnRef, long amountVnd, String orderInfo, String clientIp, String returnUrl) {
        LocalDateTime now = LocalDateTime.now(VNPAY_ZONE);
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", String.valueOf(amountVnd));  // amountVnd already multiplied by 100 in service layer
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");

        String effectiveReturnUrl =
                (returnUrl != null && !returnUrl.isBlank())
                        ? returnUrl.trim()
                        : properties.getDefaultReturnUrl();
        params.put("vnp_ReturnUrl", effectiveReturnUrl);
        params.put(
                "vnp_IpAddr",
                clientIp != null && !clientIp.isBlank()
                        ? clientIp
                        : properties.getDefaultClientIp());
        params.put("vnp_CreateDate", now.format(VNPAY_DATETIME));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(VNPAY_DATETIME));

        String query = signer.buildHashData(params);
        String secureHash = signer.sign(params, properties.getHashSecret());
        String paymentUrl = properties.getApiUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;

        // Add NotifyUrl after signature to avoid breaking hash validation
        if (properties.getIpnUrl() != null && !properties.getIpnUrl().isBlank()) {
            paymentUrl += "&vnp_NotifyUrl=" + URLEncoder.encode(properties.getIpnUrl(), StandardCharsets.UTF_8);
        }

        return paymentUrl;
    }
}
