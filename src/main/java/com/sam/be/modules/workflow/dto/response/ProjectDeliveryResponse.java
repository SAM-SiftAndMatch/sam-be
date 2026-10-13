package com.sam.be.modules.workflow.dto.response;

import com.sam.be.common.constant.enums.DeliveryStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectDeliveryResponse {
    private UUID id;
    private UUID jobId;
    private UUID submittedBy;
    private String submitterName;
    private Integer versionNumber;
    private String webUrl;
    private String repositoryUrl;
    private String note;
    private DeliveryStatus status;
    private String revisionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
