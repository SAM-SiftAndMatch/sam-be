package com.sam.be.modules.job.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobCreateRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private BigDecimal budgetMin;

    @NotNull
    private BigDecimal budgetMax;

    @NotNull
    @Future
    private LocalDateTime deadline;

    @NotBlank
    private String srsDocumentUrl;

    private Boolean isFeatured;

    private Boolean isUrgentHiring;

    private Boolean requiresAiQa;
}