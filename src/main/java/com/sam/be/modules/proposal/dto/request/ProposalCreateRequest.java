package com.sam.be.modules.proposal.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProposalCreateRequest {
    @NotNull(message = "Job id is required")
    private UUID jobId;

    private String coverLetter;

    @NotNull(message = "Proposed budget is required")
    private BigDecimal proposedBudget;

    private Integer estimatedDurationDays;

    // URL file PDF đã upload qua /storage/upload-file (tùy chọn nhưng nên có)
    private String attachmentUrl;

    private String attachmentName;
}
