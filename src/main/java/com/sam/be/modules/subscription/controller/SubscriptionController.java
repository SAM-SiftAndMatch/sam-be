package com.sam.be.modules.subscription.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.subscription.dto.request.PurchaseSubscriptionRequest;
import com.sam.be.modules.subscription.dto.response.UserSubscriptionResponse;
import com.sam.be.modules.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "Service packages: pay-per-use and monthly")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/purchase")
    @Operation(
            summary = "Purchase a service package",
            description =
                    "Pay-per-use requires the target projectId; monthly lasts 30 days with no project")
    public ApiResponse<UserSubscriptionResponse> purchase(
            @RequestBody PurchaseSubscriptionRequest request) {
        UserSubscriptionResponse response =
                subscriptionService.purchase(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.<UserSubscriptionResponse>builder().result(response).build();
    }

    @GetMapping("/me")
    @Operation(summary = "List my subscriptions", description = "All packages owned by the user")
    public ApiResponse<List<UserSubscriptionResponse>> getMine() {
        List<UserSubscriptionResponse> response =
                subscriptionService.getMine(SecurityUtils.getCurrentUserId());
        return ApiResponse.<List<UserSubscriptionResponse>>builder().result(response).build();
    }
}
