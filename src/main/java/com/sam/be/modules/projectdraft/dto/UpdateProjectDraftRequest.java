package com.sam.be.modules.projectdraft.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public record UpdateProjectDraftRequest(
        @NotNull Long expectedRevision,
        @Size(max = 255) String title,
        @Size(max = 10000) String brief,
        @Pattern(regexp = "brief|ba-chat|srs-review|risk-chat|completed|confirm|info|budget|addons|review") String phase,
        Map<String, Object> state,
        @Size(max = 500) String srsDocumentUrl) {}
