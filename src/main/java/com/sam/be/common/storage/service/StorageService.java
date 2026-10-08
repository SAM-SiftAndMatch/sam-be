package com.sam.be.common.storage.service;

public interface StorageService {
    String uploadMarkdown(String content, String fileName);

    // Upload file nhị phân (PDF/DOC...) lên Cloudinary (raw), trả secure URL
    String uploadFile(byte[] content, String fileName, String folder);

    // Tải file qua BE (tránh lỗi ACL khi bấm link Cloudinary trực tiếp), trả kèm tên + loại file
    com.sam.be.common.storage.dto.response.FileData downloadFile(String url);
}
