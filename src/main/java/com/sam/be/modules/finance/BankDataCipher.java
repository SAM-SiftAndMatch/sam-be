package com.sam.be.modules.finance;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated, versioned encryption; AAD binds ciphertext to its owner and record. */
@Component
public class BankDataCipher {
    private final Map<String, byte[]> keys = new HashMap<>();
    private final String active;
    private final byte[] fingerprintKey;
    private final SecureRandom random = new SecureRandom();

    public BankDataCipher(
            @Value("${finance.bank-active-key:v1}") String active,
            @Value("${finance.bank-keys:}") String keyring,
            @Value("${finance.bank-fingerprint-key:}") String fingerprintKey) {
        this.active = active;
        if (!keyring.isBlank())
            for (String entry : keyring.split(",")) {
                String[] pair = entry.trim().split(":", 2);
                if (pair.length != 2 || !pair[0].matches("[A-Za-z0-9_-]{1,30}"))
                    throw new IllegalStateException("Invalid bank key ring");
                byte[] key = decodeKey(pair[1]);
                if (keys.put(pair[0], key) != null)
                    throw new IllegalStateException("Duplicate bank key ID");
            }
        this.fingerprintKey = fingerprintKey.isBlank() ? null : decodeKey(fingerprintKey);
        if (!keys.isEmpty() && (!keys.containsKey(active) || this.fingerprintKey == null))
            throw new IllegalStateException("Bank encryption configuration incomplete");
    }

    private static byte[] decodeKey(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length != 32) throw new IllegalArgumentException();
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Bank keys must be Base64-encoded 32 random bytes");
        }
    }

    public boolean configured() {
        return keys.containsKey(active) && fingerprintKey != null;
    }

    private void requireConfigured() {
        if (!configured())
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình mã hóa tài khoản nhận tiền");
    }

    public String encrypt(String plaintext, String context) {
        requireConfigured();
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(keys.get(active), "AES"),
                    new GCMParameterSpec(128, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return "gcm1."
                    + active
                    + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + "."
                    + Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Bank encryption failed");
        }
    }

    public String decrypt(String encoded, String context) {
        requireConfigured();
        try {
            if (encoded == null || encoded.length() > 8192) throw new IllegalArgumentException();
            String[] p = encoded.split("\\.");
            if (p.length != 4 || !p[0].equals("gcm1") || !keys.containsKey(p[1]))
                throw new IllegalArgumentException();
            byte[] iv = Base64.getUrlDecoder().decode(p[2]);
            if (iv.length != 12) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keys.get(p[1]), "AES"),
                    new GCMParameterSpec(128, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(
                    cipher.doFinal(Base64.getUrlDecoder().decode(p[3])), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Bank data cannot be authenticated or decrypted");
        }
    }

    public String fingerprint(String value) {
        requireConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fingerprintKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Fingerprint failed");
        }
    }
}
