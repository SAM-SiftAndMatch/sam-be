package com.sam.be.common.storage.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.common.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryStorageServiceImpl implements StorageService {

    private final Cloudinary cloudinary;

    @Override
    public String uploadMarkdown(String content, String fileName) {
        try {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

            Map<String, Object> params = ObjectUtils.asMap(
                    "resource_type", "raw",
                    "public_id", "srs/" + fileName,
                    "format", "txt"
            );

            Map uploadResult = cloudinary.uploader().upload(contentBytes, params);
            return uploadResult.get("secure_url").toString();
        } catch (Exception e) {
            throw new ApiException(ErrorCode.UNEXPECTED_ERROR);
        }
    }
}