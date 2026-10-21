package com.sam.be.modules.projectdraft.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectDraftRequest(
        @NotBlank @Pattern(regexp = "AI|MANUAL") String flowType,
        @Size(max = 255) String title,
        @Size(max = 10000) String brief) {}
