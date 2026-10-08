package com.sam.be.common.storage.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.storage.service.StorageService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CloudinaryStorageServiceImpl implements StorageService {

    private final Cloudinary cloudinary;

    @Override
    public String uploadMarkdown(String content, String fileName) {
        try {
            String normalized = normalizeNewlines(content);
            String htmlContent = buildHtml(renderMarkdown(normalized));
            byte[] contentBytes = htmlContent.getBytes(StandardCharsets.UTF_8);

            Map<String, Object> params =
                    ObjectUtils.asMap(
                            "resource_type", "raw",
                            "public_id", "srs/" + fileName,
                            "format", "html");

            Map uploadResult = cloudinary.uploader().upload(contentBytes, params);
            return uploadResult.get("secure_url").toString();
        } catch (Exception e) {
            throw new ApiException(ErrorCode.UNEXPECTED_ERROR);
        }
    }

    private String normalizeNewlines(String content) {
        if (content == null) return "";
        return content.replace("\\\\r\\\\n", "\n")
                .replace("\\\\n", "\n")
                .replace("\\\\r", "\n")
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private String renderMarkdown(String markdown) {
        StringBuilder html = new StringBuilder();
        List<String> paragraph = new ArrayList<>();
        boolean inList = false;
        boolean orderedList = false;

        for (String rawLine : markdown.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                flushParagraph(html, paragraph);
                if (inList) {
                    html.append(orderedList ? "</ol>" : "</ul>");
                    inList = false;
                }
                continue;
            }

            int headingLevel = headingLevel(line);
            if (headingLevel > 0) {
                flushParagraph(html, paragraph);
                if (inList) {
                    html.append(orderedList ? "</ol>" : "</ul>");
                    inList = false;
                }
                String heading = line.substring(headingLevel).trim();
                html.append("<h")
                        .append(headingLevel)
                        .append(">")
                        .append(formatInline(heading))
                        .append("</h")
                        .append(headingLevel)
                        .append(">");
                continue;
            }

            boolean isOrdered = line.matches("^\\d+[.)]\\s+.*");
            boolean isBullet = line.matches("^[-*+]\\s+.*");
            if (isOrdered || isBullet) {
                flushParagraph(html, paragraph);
                if (!inList || orderedList != isOrdered) {
                    if (inList) html.append(orderedList ? "</ol>" : "</ul>");
                    html.append(isOrdered ? "<ol>" : "<ul>");
                    inList = true;
                    orderedList = isOrdered;
                }
                String item = line.replaceFirst(isOrdered ? "^\\d+[.)]\\s+" : "^[-*+]\\s+", "");
                html.append("<li>").append(formatInline(item)).append("</li>");
                continue;
            }

            if (inList) {
                html.append(orderedList ? "</ol>" : "</ul>");
                inList = false;
            }
            paragraph.add(line);
        }

        flushParagraph(html, paragraph);
        if (inList) html.append(orderedList ? "</ol>" : "</ul>");
        return html.toString();
    }

    private int headingLevel(String line) {
        if (line.startsWith("### ")) return 3;
        if (line.startsWith("## ")) return 2;
        if (line.startsWith("# ")) return 1;
        return 0;
    }

    private void flushParagraph(StringBuilder html, List<String> lines) {
        if (lines.isEmpty()) return;
        html.append("<p>").append(formatInline(String.join(" ", lines))).append("</p>");
        lines.clear();
    }

    private String formatInline(String text) {
        String safe = escapeHtml(text);
        safe = safe.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        safe = safe.replaceAll("__(.+?)__", "<strong>$1</strong>");
        safe = safe.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<em>$1</em>");
        safe = safe.replaceAll("(?<!_)_([^_]+)_(?!_)", "<em>$1</em>");
        return safe;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String buildHtml(String renderedContent) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"vi\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Tài liệu SRS</title><style>"
                + "*{box-sizing:border-box}body{margin:0;background:#f1f5f9;color:#1f2937;"
                + "font-family:Arial,'Segoe UI',sans-serif;line-height:1.75}"
                + ".document{max-width:900px;margin:36px auto;padding:48px 56px;background:#fff;"
                + "border-radius:18px;box-shadow:0 8px 32px rgba(15,23,42,.08)}"
                + "h1{font-size:26px;color:#1d4ed8;border-bottom:2px solid #dbeafe;padding-bottom:12px;margin:0 0 24px}"
                + "h2{font-size:21px;color:#1d4ed8;margin:32px 0 12px}"
                + "h3{font-size:17px;color:#334155;margin:24px 0 8px}"
                + "p{margin:0 0 14px;white-space:normal}ul,ol{padding-left:28px;margin:8px 0 18px}"
                + "li{padding-left:4px;margin:6px 0}strong{color:#111827;font-weight:700}"
                + "@media(max-width:640px){.document{margin:0;padding:28px 22px;border-radius:0}h1{font-size:22px}}"
                + "</style></head><body><main class=\"document\">"
                + renderedContent
                + "</main></body></html>";
    }
}
