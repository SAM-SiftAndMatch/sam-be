package com.sam.be.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Safe, bounded diagnostic metadata. Never attach the upstream body, URL token or exception cause.
 */
public class PaymentGatewayException extends ResponseStatusException {
    private final Map<String, String> diagnostics;

    public PaymentGatewayException(
            HttpStatus status,
            String reason,
            String reference,
            Integer upstreamStatus,
            Integer resultCode,
            String message,
            Map<String, String> callbackOrigins) {
        super(status, message + " Mã hỗ trợ: " + reference);
        var data = new LinkedHashMap<String, String>();
        data.put("provider", "PAY2S");
        data.put("reason", reason);
        data.put("reference", reference);
        if (upstreamStatus != null) data.put("upstreamHttpStatus", upstreamStatus.toString());
        if (resultCode != null) data.put("upstreamResultCode", resultCode.toString());
        data.putAll(callbackOrigins);
        diagnostics = Map.copyOf(data);
    }

    public Map<String, String> diagnostics() {
        return diagnostics;
    }
}
