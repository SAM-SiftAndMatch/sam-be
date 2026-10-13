package com.sam.be.modules.workflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DeliveryCreateRequest {
    @NotBlank
    @Pattern(regexp = "^https?://.+", message = "Web URL must use http or https")
    private String webUrl;

    @Pattern(regexp = "^$|^https?://.+", message = "Repository URL must use http or https")
    private String repositoryUrl;

    private String note;
}
