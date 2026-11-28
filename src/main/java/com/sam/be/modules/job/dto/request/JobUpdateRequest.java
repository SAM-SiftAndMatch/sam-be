package com.sam.be.modules.job.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class JobUpdateRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank private String description;

    @NotNull
    @DecimalMin("3000000")
    @Digits(integer = 15, fraction = 0)
    private BigDecimal budgetMin;

    @NotNull
    @DecimalMin("3000000")
    @Digits(integer = 15, fraction = 0)
    private BigDecimal budgetMax;

    @DecimalMin("0.1")
    private BigDecimal estimatedDurationMonths;

    private LocalDateTime deadline;
}
