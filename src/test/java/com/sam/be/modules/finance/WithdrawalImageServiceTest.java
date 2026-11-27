package com.sam.be.modules.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class WithdrawalImageServiceTest {
    static MockMultipartFile png() {
        byte[] image =
                Base64.getDecoder()
                        .decode(
                                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aWQkAAAAASUVORK5CYII=");
        return new MockMultipartFile("images", "proof.png", "image/png", image);
    }

    private final FinanceStore store = mock(FinanceStore.class);
    private final WithdrawalImageService images =
            new WithdrawalImageService(store, BankDataCipherTest.cipher());

    @Test
    void requiresOneToFiveRealImagesWithAllowedFormatsAndSize() {
        assertThrows(RuntimeException.class, () -> images.validate(null));
        assertThrows(RuntimeException.class, () -> images.validate(List.of()));
        assertThrows(RuntimeException.class, () -> images.validate(Collections.nCopies(6, png())));
        assertThrows(
                RuntimeException.class,
                () ->
                        images.validate(
                                List.of(
                                        new MockMultipartFile(
                                                "images",
                                                "proof.png",
                                                "image/png",
                                                "not-an-image".getBytes()))));
        assertThrows(
                RuntimeException.class,
                () ->
                        images.validate(
                                List.of(
                                        new MockMultipartFile(
                                                "images",
                                                "proof.svg",
                                                "image/png",
                                                png().getBytes()))));
        assertThrows(
                RuntimeException.class,
                () ->
                        images.validate(
                                List.of(
                                        new MockMultipartFile(
                                                "images",
                                                "proof.png",
                                                "image/png",
                                                new byte[5 * 1024 * 1024 + 1]))));
        var result = images.validate(List.of(png()));
        assertEquals("image/png", result.getFirst().type());
        assertEquals(64, result.getFirst().sha256().length());
        verifyNoInteractions(store);
    }

    @Test
    void anotherFreelancerOrClientCannotReadEvidence() {
        UUID owner = UUID.randomUUID(), withdrawal = UUID.randomUUID();
        when(store.one("SELECT owner_id FROM finance_withdrawals WHERE id=?", withdrawal))
                .thenReturn(Map.of("ownerId", owner));
        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> images.read(UUID.randomUUID(), false, withdrawal, UUID.randomUUID()));
        verify(store, never()).one(contains("finance_withdrawal_images"), any(), any());
    }

    @Test
    void ownerAndAdminReadTheBoundEncryptedImage() throws Exception {
        UUID owner = UUID.randomUUID(), withdrawal = UUID.randomUUID(), image = UUID.randomUUID();
        var cipher = BankDataCipherTest.cipher();
        byte[] content = png().getBytes();
        when(store.one("SELECT owner_id FROM finance_withdrawals WHERE id=?", withdrawal))
                .thenReturn(Map.of("ownerId", owner));
        when(store.one(
                        "SELECT * FROM finance_withdrawal_images WHERE id=? AND withdrawal_id=?",
                        image,
                        withdrawal))
                .thenReturn(
                        Map.of(
                                "fileName",
                                "proof.png",
                                "contentType",
                                "image/png",
                                "contentEncrypted",
                                cipher.encryptBytes(
                                        content, "withdrawal-image:" + owner + ":" + image)));
        assertArrayEquals(content, images.read(owner, false, withdrawal, image).bytes());
        assertArrayEquals(content, images.read(UUID.randomUUID(), true, withdrawal, image).bytes());
    }
}
