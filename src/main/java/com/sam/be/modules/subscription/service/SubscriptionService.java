package com.sam.be.modules.subscription.service;

import com.sam.be.modules.subscription.dto.request.PurchaseSubscriptionRequest;
import com.sam.be.modules.subscription.dto.response.UserSubscriptionResponse;
import java.util.List;
import java.util.UUID;

public interface SubscriptionService {
    UserSubscriptionResponse purchase(UUID userId, PurchaseSubscriptionRequest request);

    List<UserSubscriptionResponse> getMine(UUID userId);
}
