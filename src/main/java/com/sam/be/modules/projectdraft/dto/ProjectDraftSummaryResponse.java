package com.sam.be.modules.projectdraft.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectDraftSummaryResponse(
        UUID id,
        String flowType,
        String phase,
        int progressStep,
        int progressPercent,
        String title,
        String brief,
        LocalDateTime updatedAt) {}
