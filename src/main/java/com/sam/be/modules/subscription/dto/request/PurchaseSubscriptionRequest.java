package com.sam.be.modules.subscription.dto.request;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseSubscriptionRequest {

    private UUID packageId;

    // Bắt buộc với gói lẻ PAY_PER_USE (project áp dụng), bỏ trống với gói tháng.
    private UUID projectId;
}
