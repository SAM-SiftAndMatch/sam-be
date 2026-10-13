package com.sam.be.modules.workflow.dto.response;

import com.sam.be.modules.wallet.dto.response.ProjectSettlementResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveryAcceptanceResponse {
    private ProjectDeliveryResponse delivery;
    private ProjectSettlementResponse settlement;
}
