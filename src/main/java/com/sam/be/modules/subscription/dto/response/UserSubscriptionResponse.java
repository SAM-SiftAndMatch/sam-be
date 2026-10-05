package com.sam.be.modules.subscription.dto.response;

import com.sam.be.common.constant.enums.SubscriptionStatus;
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
public class UserSubscriptionResponse {

    private UUID id;

    private UUID packageId;

    private SubscriptionStatus status;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private UUID targetProjectId;

    // URL VNPay, chỉ có khi vừa tạo đơn PENDING (FE redirect sang đó).
    private String vnpayUrl;
}
