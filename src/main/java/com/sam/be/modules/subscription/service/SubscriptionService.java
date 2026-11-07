package com.sam.be.modules.subscription.service;

import com.sam.be.common.response.PageResponse;
import com.sam.be.modules.subscription.dto.request.PurchaseSubscriptionRequest;
import com.sam.be.modules.subscription.dto.response.UserSubscriptionResponse;
import java.util.List;
import java.util.UUID;

public interface SubscriptionService {
    UserSubscriptionResponse purchase(UUID userId, PurchaseSubscriptionRequest request);

    String activateVerifiedPayment(UUID id, String environment, java.math.BigDecimal amount);

    UserSubscriptionResponse confirmPayment(UUID userId, UUID subscriptionId);

    List<UserSubscriptionResponse> getMine(UUID userId);

    PageResponse<UserSubscriptionResponse> getMinePage(UUID userId, int page, int size);
}
