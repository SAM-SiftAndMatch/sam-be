package com.sam.be.modules.subscription.service;

import com.sam.be.common.response.PageResponse;
import com.sam.be.modules.subscription.dto.request.PurchaseSubscriptionRequest;
import com.sam.be.modules.subscription.dto.response.UserSubscriptionResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SubscriptionService {
    UserSubscriptionResponse purchase(UUID userId, PurchaseSubscriptionRequest request);

    Map<String, String> handleVnpayIpn(Map<String, String> params);

    UserSubscriptionResponse confirmPayment(UUID userId, UUID subscriptionId);

    List<UserSubscriptionResponse> getMine(UUID userId);

    PageResponse<UserSubscriptionResponse> getMinePage(UUID userId, int page, int size);
}
