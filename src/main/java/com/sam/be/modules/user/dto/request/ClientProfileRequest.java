package com.sam.be.modules.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfileRequest {

    @NotBlank(message = "Company name is required")
    private String companyName;

    private String industry;

    private String websiteUrl;

    private String description;
}