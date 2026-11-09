package com.sam.be.modules.chat.dto;

import java.util.UUID;

public record ChatAttachmentDto(UUID id, String fileName, String contentType, long sizeBytes) {}
