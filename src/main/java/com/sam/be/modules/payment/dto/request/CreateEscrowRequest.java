package com.sam.be.modules.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateEscrowRequest {

    @NotNull(message = "Contract id is required")
    private UUID contractId;
}
