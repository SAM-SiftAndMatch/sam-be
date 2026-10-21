package com.sam.be.modules.projectdraft.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateDraftAiTurnRequest(
        @NotNull UUID clientMessageId,
        @NotBlank @Pattern(regexp = "BA|RISK") String phase,
        @NotBlank @Size(max = 10000) String userMessage) {}
