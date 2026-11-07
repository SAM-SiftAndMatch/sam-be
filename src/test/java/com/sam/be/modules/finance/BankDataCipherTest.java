package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class BankDataCipherTest {
    static String key(int number) {
        byte[] b = new byte[32];
        Arrays.fill(b, (byte) number);
        return Base64.getEncoder().encodeToString(b);
    }

    static BankDataCipher cipher() {
        return new BankDataCipher("v1", "v1:" + key(1), key(3));
    }

    @Test
    void roundTripUsesRandomIvAndDoesNotContainPlaintext() {
        var c = cipher();
        String a = c.encrypt("00123456789|NGUYEN VAN A", "bank:owner:1"),
                b = c.encrypt("00123456789|NGUYEN VAN A", "bank:owner:1");
        assertNotEquals(a, b);
        assertFalse(a.contains("00123456789"));
        assertEquals("00123456789|NGUYEN VAN A", c.decrypt(a, "bank:owner:1"));
    }

    @Test
    void cannotMoveCiphertextToAnotherOwnerOrRecord() {
        var c = cipher();
        String encrypted = c.encrypt("secret", "bank:owner:1");
        assertThrows(IllegalStateException.class, () -> c.decrypt(encrypted, "bank:other:1"));
        assertThrows(IllegalStateException.class, () -> c.decrypt(encrypted, "bank:owner:2"));
    }

    @Test
    void tamperingFailsAuthentication() {
        var c = cipher();
        String[] parts = c.encrypt("secret", "owner").split("\\.");
        byte[] bytes = Base64.getUrlDecoder().decode(parts[3]);
        bytes[0] ^= 1;
        parts[3] = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        assertThrows(
                IllegalStateException.class, () -> c.decrypt(String.join(".", parts), "owner"));
    }

    @Test
    void rotationRetainsOldDecryptionButUsesNewKey() {
        var old = cipher();
        String encrypted = old.encrypt("secret", "owner");
        var next = new BankDataCipher("v2", "v1:" + key(1) + ",v2:" + key(2), key(3));
        assertEquals("secret", next.decrypt(encrypted, "owner"));
        assertTrue(next.encrypt("new", "owner").startsWith("gcm1.v2."));
        assertEquals(old.fingerprint("bank-number"), next.fingerprint("bank-number"));
    }

    @Test
    void missingKeysFailClosed() {
        var c = new BankDataCipher("v1", "", "");
        assertFalse(c.configured());
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> c.encrypt("secret", "owner"));
    }

    @Test
    void invalidKeyOrMissingFingerprintRejectsConfiguration() {
        assertThrows(
                IllegalStateException.class, () -> new BankDataCipher("v1", "v1:YWJj", key(3)));
        assertThrows(
                IllegalStateException.class, () -> new BankDataCipher("v1", "v1:" + key(1), ""));
    }

    @Test
    void fingerprintsAreKeyedAndNamespaced() {
        var c = cipher();
        assertNotEquals(c.fingerprint("owner1:VCB:00123456"), c.fingerprint("owner2:VCB:00123456"));
        assertNotEquals(c.fingerprint("owner:VCB:00123456"), c.fingerprint("owner:ACB:00123456"));
    }
}
