package com.sam.be.modules.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeliveryRevisionRequest {
    @NotBlank(message = "Revision reason is required")
    @Size(max = 5000, message = "Revision reason must not exceed 5000 characters")
    private String reason;
}
