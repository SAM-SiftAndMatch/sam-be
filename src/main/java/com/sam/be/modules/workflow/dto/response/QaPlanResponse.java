package com.sam.be.modules.workflow.dto.response;

import com.sam.be.common.constant.enums.QaPlanStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QaPlanResponse {
    private UUID id;
    private UUID deliveryId;
    private QaPlanStatus status;
    private LocalDateTime generatedAt;
    private List<QaChecklistItemResponse> checklistItems;
}
