package com.sam.be.modules.projectdraft.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProjectDraftResponse(
        UUID id,
        String flowType,
        String phase,
        String status,
        String title,
        String brief,
        Map<String, Object> state,
        String srsContent,
        String srsDocumentUrl,
        BigDecimal exactBudgetVnd,
        BigDecimal durationMonths,
        UUID publishedJobId,
        long version,
        long editRevision,
        LocalDateTime updatedAt,
        List<ProjectDraftAiTurnResponse> turns) {}
