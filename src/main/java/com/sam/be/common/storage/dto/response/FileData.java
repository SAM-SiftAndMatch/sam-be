package com.sam.be.common.storage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileData {
    private byte[] content;
    private String contentType;
    private String fileName;
}
