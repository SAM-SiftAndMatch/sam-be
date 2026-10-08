package com.sam.be.modules.job.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobCreateRequest {

    @NotBlank private String title;

    @NotBlank private String description;

    @NotNull private BigDecimal budgetMin;

    @NotNull private BigDecimal budgetMax;

    @NotNull private BigDecimal estimatedDurationMonths;

    private LocalDateTime deadline;

    @NotBlank private String srsDocumentUrl;

    /** Nội dung SRS Markdown (lưu vào DB để render đẹp trong app) */
    private String srsContent;

    private Boolean isFeatured;

    private Boolean isUrgentHiring;

    private Boolean requiresAiQa;
}
