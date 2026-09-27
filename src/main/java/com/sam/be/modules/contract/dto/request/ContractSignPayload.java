package com.sam.be.modules.contract.dto.request;

import lombok.Data;
import java.util.UUID;

@Data
public class ContractSignPayload {
    private UUID senderId;
    private Boolean isAgreed;
}