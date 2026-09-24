package com.sam.be.common.storage.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSrsRequest {
    @NotBlank
    private String content;

    @NotBlank
    private String fileName;
}