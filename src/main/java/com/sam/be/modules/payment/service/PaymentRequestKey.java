package com.sam.be.modules.payment.service;

import com.sam.be.modules.finance.FinanceStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Request metadata only; financial identity is always checked against the database. */
final class PaymentRequestKey {
    private static final String ATTRIBUTE = PaymentRequestKey.class.getName();

    record Context(UUID owner, String purpose, String keyHash, String payloadHash) {}

    static Context prepare(UUID owner, String purpose, String payload) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a))
            return null;
        String key = a.getRequest().getHeader("Idempotency-Key");
        if (key == null) return null; // Older clients still have business/database uniqueness.
        if (!key.matches("[A-Za-z0-9._:-]{16,128}"))
            throw FinanceStore.invalid("Idempotency-Key phải có 16-128 ký tự hợp lệ");
        Context context = new Context(owner, purpose, hash(key), hash(purpose + ":" + payload));
        a.getRequest().setAttribute(ATTRIBUTE, context);
        return context;
    }

    static Context current(UUID owner, String purpose) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a))
            return null;
        Object value = a.getRequest().getAttribute(ATTRIBUTE);
        if (value instanceof Context c && c.owner().equals(owner) && c.purpose().equals(purpose))
            return c;
        return null;
    }

    static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
