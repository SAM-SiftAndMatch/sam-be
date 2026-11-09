package com.sam.be.modules.chat.service;

import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class ChatFilePolicy {
    public static final int MAX_ATTACHMENTS = 5;
    public static final long IMAGE_LIMIT = 5L * 1024 * 1024;
    public static final long FILE_LIMIT = 10L * 1024 * 1024;
    public static final long MESSAGE_LIMIT = 25L * 1024 * 1024;
    private static final Map<String, String> TYPES =
            Map.ofEntries(
                    Map.entry("jpg", "image/jpeg"),
                    Map.entry("jpeg", "image/jpeg"),
                    Map.entry("png", "image/png"),
                    Map.entry("gif", "image/gif"),
                    Map.entry("webp", "image/webp"),
                    Map.entry("pdf", "application/pdf"),
                    Map.entry("doc", "application/msword"),
                    Map.entry(
                            "docx",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    Map.entry("xls", "application/vnd.ms-excel"),
                    Map.entry(
                            "xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    Map.entry("txt", "text/plain"),
                    Map.entry("csv", "text/csv"),
                    Map.entry("zip", "application/zip"));

    private ChatFilePolicy() {}

    public static String safeName(String value) {
        String name = value == null ? "file" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank() || name.length() > 200)
            throw invalid("Tên file không hợp lệ (tối đa 200 ký tự).");
        return name;
    }

    public static String validate(String name, byte[] bytes) {
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type = TYPES.get(extension);
        if (type == null)
            throw invalid(
                    "Chỉ nhận ảnh JPG/PNG/GIF/WebP và file PDF, DOC/DOCX, XLS/XLSX, TXT, CSV, ZIP.");
        if (bytes == null || bytes.length == 0) throw invalid("File trống.");
        long limit = type.startsWith("image/") ? IMAGE_LIMIT : FILE_LIMIT;
        if (bytes.length > limit)
            throw invalid(type.startsWith("image/") ? "Ảnh tối đa 5MB." : "File tối đa 10MB.");
        boolean valid =
                switch (extension) {
                    case "jpg", "jpeg" -> starts(bytes, 0xff, 0xd8, 0xff);
                    case "png" -> starts(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
                    case "gif" -> ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a");
                    case "webp" -> ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP");
                    case "pdf" -> ascii(bytes, 0, "%PDF-");
                    case "doc", "xls" ->
                            starts(bytes, 0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1);
                    case "docx" -> officeZip(bytes, "word/");
                    case "xlsx" -> officeZip(bytes, "xl/");
                    case "zip" ->
                            starts(bytes, 0x50, 0x4b, 0x03, 0x04)
                                    || starts(bytes, 0x50, 0x4b, 0x05, 0x06);
                    case "txt", "csv" -> validText(bytes);
                    default -> false;
                };
        if (!valid) throw invalid("Nội dung file không khớp định dạng được phép.");
        if (type.startsWith("image/") && !extension.equals("webp")) {
            // Read metadata without decoding an arbitrarily large image into memory.
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid("Ảnh không hợp lệ.");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 25_000_000L)
                        throw invalid("Ảnh tối đa 25 megapixel.");
                } finally {
                    reader.dispose();
                }
            } catch (IOException e) {
                throw invalid("Ảnh không hợp lệ.");
            }
        }
        return type;
    }

    private static boolean starts(byte[] b, int... signature) {
        if (b.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if ((b[i] & 255) != signature[i]) return false;
        return true;
    }

    private static boolean ascii(byte[] b, int offset, String value) {
        if (b.length < offset + value.length()) return false;
        return new String(b, offset, value.length(), StandardCharsets.US_ASCII).equals(value);
    }

    private static boolean officeZip(byte[] b, String prefix) {
        if (!starts(b, 0x50, 0x4b, 0x03, 0x04)) return false;
        // Central-directory names suffice; never decompress potentially hostile office archives.
        String directory = new String(b, StandardCharsets.ISO_8859_1);
        return directory.contains("[Content_Types].xml") && directory.contains(prefix);
    }

    private static boolean validText(byte[] bytes) {
        try {
            String text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
            return text.chars().noneMatch(c -> c == 0 || (c < 32 && c != 9 && c != 10 && c != 13));
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    public static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }
}
