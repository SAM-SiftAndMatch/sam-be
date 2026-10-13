package com.sam.be.modules.workflow.service;

import com.sam.be.modules.workflow.dto.response.QaPlanResponse;
import com.sam.be.modules.workflow.entity.ProjectDelivery;
import com.sam.be.modules.workflow.entity.ProjectTimelineTask;
import java.util.List;
import java.util.UUID;

public interface ManualQaPlanService {
    QaPlanResponse generateForDelivery(
            ProjectDelivery delivery, String srsContent, List<ProjectTimelineTask> tasks);

    QaPlanResponse getByDeliveryId(UUID deliveryId);
}
