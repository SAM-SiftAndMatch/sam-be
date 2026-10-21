package com.sam.be.modules.projectdraft.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateProjectDraftRequest(
        @NotNull Long expectedRevision,
        @Size(max = 255) String title,
        @Size(max = 10000) String brief,
        @Pattern(regexp = "brief|ba-chat|srs-review|risk-chat|completed|confirm|info|budget|addons|review") String phase,
        JsonNode state,
        @Size(max = 500) String srsDocumentUrl) {}
