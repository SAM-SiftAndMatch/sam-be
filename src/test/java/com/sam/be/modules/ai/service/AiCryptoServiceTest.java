package com.sam.be.modules.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class AiCryptoServiceTest {

    @Test
    void encryptsAndDecryptsWithRandomIv() {
        var crypto = new AiCryptoService("super-secret-passphrase-for-test");
        assertTrue(crypto.isConfigured());

        String plain = "AIzaSyD-1234567890-abcdef";
        String c1 = crypto.encrypt(plain);
        String c2 = crypto.encrypt(plain);

        assertNotEquals(c1, c2);
        assertEquals(plain, crypto.decrypt(c1));
        assertEquals(plain, crypto.decrypt(c2));
    }

    @Test
    void missingCryptoKeyThrows503ApiException() {
        var crypto = new AiCryptoService("  ");
        assertFalse(crypto.isConfigured());

        ApiException ex = assertThrows(ApiException.class, () -> crypto.encrypt("AIzaSyTest"));
        assertEquals(ErrorCode.AI_CRYPTO_NOT_CONFIGURED, ex.getErrorCode());
    }

    @Test
    void wrongCryptoKeyCannotDecrypt() {
        var writer = new AiCryptoService("key-one");
        var reader = new AiCryptoService("key-two");

        String encrypted = writer.encrypt("AIzaSyTestSecretKey");
        assertThrows(IllegalStateException.class, () -> reader.decrypt(encrypted));
    }

    @Test
    void masksAndFingerprintsDeterministically() {
        assertEquals("AIza••••cdef", AiCryptoService.mask("AIzaSy123456abcdef"));
        assertEquals(
                AiCryptoService.fingerprint("  AIzaSyTest  "),
                AiCryptoService.fingerprint("AIzaSyTest"));
        assertEquals(64, AiCryptoService.fingerprint("AIzaSyTest").length());
    }
}
