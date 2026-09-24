package com.sam.be.modules.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfileResponse {

    private UUID id;

    private UUID userId;

    private String fullName;

    private String email;

    private String companyName;

    private String industry;

    private String websiteUrl;

    private String description;
}