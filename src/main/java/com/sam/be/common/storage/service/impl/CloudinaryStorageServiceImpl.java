package com.sam.be.common.storage.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.storage.service.StorageService;
import java.nio.charset.StandardCharsets;
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
            // Bọc nội dung markdown vào HTML chuẩn UTF-8 + font tiếng Việt đẹp
            // để khi mở link trên trình duyệt không bị lỗi font/encoding.
            String escapedContent =
                    content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String htmlContent =
                    "<!DOCTYPE html>\n"
                            + "<html lang=\"vi\">\n"
                            + "<head>\n"
                            + "  <meta charset=\"UTF-8\">\n"
                            + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                            + "  <title>SRS - Tài liệu đặc tả yêu cầu</title>\n"
                            + "  <style>\n"
                            + "    body {\n"
                            + "      font-family: 'Segoe UI', Roboto, Arial, sans-serif;\n"
                            + "      background: #F8FAFC;\n"
                            + "      color: #1F2937;\n"
                            + "      margin: 0;\n"
                            + "      line-height: 1.7;\n"
                            + "    }\n"
                            + "    .container {\n"
                            + "      max-width: 820px;\n"
                            + "      margin: 32px auto;\n"
                            + "      background: #fff;\n"
                            + "      border-radius: 16px;\n"
                            + "      box-shadow: 0 4px 24px rgba(0,0,0,0.06);\n"
                            + "      padding: 40px 48px;\n"
                            + "    }\n"
                            + "    pre {\n"
                            + "      white-space: pre-wrap;\n"
                            + "      word-wrap: break-word;\n"
                            + "      font-family: 'Segoe UI', Roboto, Arial, sans-serif;\n"
                            + "      font-size: 15px;\n"
                            + "      margin: 0;\n"
                            + "    }\n"
                            + "    h1 { font-size: 20px; margin-bottom: 16px; padding-bottom: 8px; "
                            + "border-bottom: 2px solid #1D4ED8; color: #111827; }\n"
                            + "    h2 { font-size: 17px; margin-top: 24px; color: #1D4ED8; }\n"
                            + "    h3 { font-size: 15px; margin-top: 16px; color: #374151; }\n"
                            + "    strong { color: #111827; }\n"
                            + "    a { color: #1D4ED8; }\n"
                            + "  </style>\n"
                            + "</head>\n"
                            + "<body>\n"
                            + "  <div class=\"container\">\n"
                            + "    <pre>" + escapedContent + "</pre>\n"
                            + "  </div>\n"
                            + "</body>\n"
                            + "</html>";

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
}
