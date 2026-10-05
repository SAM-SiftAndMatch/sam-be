package com.sam.be.modules.payment.dto.response;

import com.sam.be.common.constant.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID paymentId;

    private UUID contractId;

    private String installment;

    private BigDecimal amount;

    private String currency;

    private PaymentStatus status;

    private String vnpayUrl;

    private LocalDateTime escrowHeldAt;

    private LocalDateTime releasedAt;
}
