package com.sam.be.modules.contract.dto.request;

import java.util.UUID;
import lombok.Data;

@Data
public class ContractSignPayload {
    private UUID senderId;
    private Boolean isAgreed;
}
