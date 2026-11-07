package com.sam.be.modules.subscription.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.response.PageResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.subscription.dto.request.PurchaseSubscriptionRequest;
import com.sam.be.modules.subscription.dto.response.UserSubscriptionResponse;
import com.sam.be.modules.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "Service packages: pay-per-use and monthly")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/purchase")
    @com.sam.be.common.security.annotation.RateLimit(
            type = com.sam.be.common.security.annotation.RateLimit.Type.USER_ID,
            action = "subscription_checkout",
            limit = 5,
            duration = 60,
            failClosed = true)
    @Operation(
            summary = "Purchase a service package",
            description =
                    "Creates a PENDING order and returns the Pay2S URL. Pay-per-use requires the target"
                            + " projectId; monthly lasts 30 days from activation with no project")
    public ApiResponse<UserSubscriptionResponse> purchase(
            @RequestBody PurchaseSubscriptionRequest request) {
        UserSubscriptionResponse response =
                subscriptionService.purchase(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.<UserSubscriptionResponse>builder().result(response).build();
    }

    @PostMapping("/{id}/confirm-payment")
    @Operation(
            summary = "Read server-verified subscription state after Pay2S redirect",
            description = "Only the signed Pay2S IPN can activate a pending subscription")
    public ApiResponse<UserSubscriptionResponse> confirmPayment(@PathVariable String id) {
        UserSubscriptionResponse response =
                subscriptionService.confirmPayment(
                        SecurityUtils.getCurrentUserId(), UUID.fromString(id));
        return ApiResponse.<UserSubscriptionResponse>builder().result(response).build();
    }

    @GetMapping("/me")
    @Operation(summary = "List my subscriptions", description = "All packages owned by the user")
    public ApiResponse<List<UserSubscriptionResponse>> getMine() {
        List<UserSubscriptionResponse> response =
                subscriptionService.getMine(SecurityUtils.getCurrentUserId());
        return ApiResponse.<List<UserSubscriptionResponse>>builder().result(response).build();
    }

    @GetMapping("/me/page")
    @Operation(summary = "Get a page of my subscriptions")
    public ApiResponse<PageResponse<UserSubscriptionResponse>> getMinePage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<UserSubscriptionResponse>>builder()
                .result(
                        subscriptionService.getMinePage(
                                SecurityUtils.getCurrentUserId(), page, size))
                .build();
    }
}
