package com.sam.be.modules.finance;

import static com.sam.be.modules.finance.FinanceStore.*;

import com.sam.be.modules.chat.service.ChatFilePolicy;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Evidence stays encrypted in the finance store; no public storage URL is issued. */
@Service
@RequiredArgsConstructor
public class WithdrawalImageService {
    private final FinanceStore store;
    private final BankDataCipher cipher;

    public record Image(String name, String type, byte[] bytes, String sha256) {
        @Override
        public String toString() {
            return "WithdrawalImage[REDACTED]";
        }
    }

    public List<Image> validate(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 5)
            throw invalid("Cần từ 1 đến 5 ảnh bằng chứng hoặc biên lai");
        List<Image> images = new ArrayList<>();
        for (var file : files) {
            if (file == null || file.isEmpty() || file.getSize() > ChatFilePolicy.IMAGE_LIMIT)
                throw invalid("Mỗi ảnh bằng chứng phải có nội dung và tối đa 5 MB");
            String name = ChatFilePolicy.safeName(file.getOriginalFilename());
            String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (!Set.of("jpg", "jpeg", "png", "webp").contains(extension))
                throw invalid("Ảnh bằng chứng chỉ nhận JPG, PNG hoặc WebP");
            try {
                byte[] bytes = file.getBytes();
                String type = ChatFilePolicy.validate(name, bytes);
                String hash =
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                images.add(new Image(name, type, bytes, hash));
            } catch (IOException | java.security.NoSuchAlgorithmException ex) {
                throw invalid("Không đọc được ảnh bằng chứng");
            }
        }
        return images;
    }

    public void save(UUID owner, UUID withdrawal, List<Image> images) {
        for (var image : images) {
            UUID id = UUID.randomUUID();
            store.jdbc()
                    .update(
                            "INSERT INTO finance_withdrawal_images(id,withdrawal_id,file_name,content_type,size_bytes,sha256,content_encrypted,created_at) VALUES (?,?,?,?,?,?,?,?)",
                            id,
                            withdrawal,
                            image.name(),
                            image.type(),
                            image.bytes().length,
                            image.sha256(),
                            cipher.encryptBytes(image.bytes(), context(owner, id)),
                            store.time(store.now()));
        }
    }

    public List<Map<String, Object>> metadata(UUID withdrawal) {
        return store.rows(
                "SELECT id,file_name,content_type,size_bytes FROM finance_withdrawal_images WHERE withdrawal_id=? ORDER BY id",
                withdrawal);
    }

    public boolean matches(UUID withdrawal, List<Image> images) {
        var stored =
                store
                        .rows(
                                "SELECT file_name,sha256 FROM finance_withdrawal_images WHERE withdrawal_id=?",
                                withdrawal)
                        .stream()
                        .map(row -> row.get("fileName") + ":" + row.get("sha256"))
                        .sorted()
                        .toList();
        return stored.equals(
                images.stream()
                        .map(image -> image.name() + ":" + image.sha256())
                        .sorted()
                        .toList());
    }

    public record Content(String name, String type, byte[] bytes) {}

    public static String receiptReference(UUID withdrawal) {
        return "CT-" + withdrawal.toString().toUpperCase(Locale.ROOT);
    }

    public void saveReceipts(UUID actor, UUID withdrawal, List<Image> images) {
        for (var image : images) {
            UUID imageId = UUID.randomUUID();
            store.jdbc()
                    .update(
                            "INSERT INTO finance_payout_receipt_images(id,withdrawal_id,uploaded_by,file_name,content_type,size_bytes,sha256,content_encrypted,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                            imageId,
                            withdrawal,
                            actor,
                            image.name(),
                            image.type(),
                            image.bytes().length,
                            image.sha256(),
                            cipher.encryptBytes(image.bytes(), receiptContext(withdrawal, imageId)),
                            store.time(store.now()));
        }
    }

    public List<Map<String, Object>> receiptMetadata(UUID withdrawal) {
        return store.rows(
                "SELECT id,file_name,content_type,size_bytes FROM finance_payout_receipt_images WHERE withdrawal_id=? ORDER BY id",
                withdrawal);
    }

    public boolean receiptMatches(UUID withdrawal, List<Image> images) {
        var stored =
                store
                        .rows(
                                "SELECT file_name,sha256 FROM finance_payout_receipt_images WHERE withdrawal_id=?",
                                withdrawal)
                        .stream()
                        .map(row -> row.get("fileName") + ":" + row.get("sha256"))
                        .sorted()
                        .toList();
        return stored.equals(
                images.stream()
                        .map(image -> image.name() + ":" + image.sha256())
                        .sorted()
                        .toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Content readReceipt(UUID withdrawal, UUID imageId) {
        var image =
                store.one(
                        "SELECT * FROM finance_payout_receipt_images WHERE id=? AND withdrawal_id=?",
                        imageId,
                        withdrawal);
        return new Content(
                image.get("fileName").toString(),
                image.get("contentType").toString(),
                cipher.decryptBytes(
                        image.get("contentEncrypted").toString(),
                        receiptContext(withdrawal, imageId)));
    }

    private String receiptContext(UUID withdrawal, UUID image) {
        return "payout-receipt:" + withdrawal + ":" + image;
    }

    @Transactional(readOnly = true)
    public Content read(UUID actor, boolean admin, UUID withdrawal, UUID imageId) {
        var request = store.one("SELECT owner_id FROM finance_withdrawals WHERE id=?", withdrawal);
        UUID owner = id(request, "ownerId");
        if (!admin && !owner.equals(actor)) throw missing();
        var image =
                store.one(
                        "SELECT * FROM finance_withdrawal_images WHERE id=? AND withdrawal_id=?",
                        imageId,
                        withdrawal);
        return new Content(
                image.get("fileName").toString(),
                image.get("contentType").toString(),
                cipher.decryptBytes(
                        image.get("contentEncrypted").toString(), context(owner, imageId)));
    }

    private String context(UUID owner, UUID image) {
        return "withdrawal-image:" + owner + ":" + image;
    }
}
