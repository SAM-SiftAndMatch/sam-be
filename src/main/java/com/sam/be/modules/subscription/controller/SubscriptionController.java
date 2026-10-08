package com.sam.be.modules.subscription.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.subscription.dto.request.PurchaseSubscriptionRequest;
import com.sam.be.modules.subscription.dto.response.UserSubscriptionResponse;
import com.sam.be.modules.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
                    "Creates a PENDING order and returns the VNPay URL. Pay-per-use requires the target"
                            + " projectId; monthly lasts 30 days from activation with no project")
    public ApiResponse<UserSubscriptionResponse> purchase(
            @RequestBody PurchaseSubscriptionRequest request) {
        UserSubscriptionResponse response =
                subscriptionService.purchase(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.<UserSubscriptionResponse>builder().result(response).build();
    }

    @PostMapping("/vnpay-ipn")
    @Operation(
            summary = "VNPay IPN webhook for packages",
            description = "Server-to-server callback from VNPay, checksum verified, no JWT needed")
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(subscriptionService.handleVnpayIpn(params));
    }

    @PostMapping("/{id}/confirm-payment")
    @Operation(
            summary = "Confirm subscription payment after VNPay redirect",
            description =
                    "FE calls this after VNPay redirects back with success code to activate pending subscription")
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
}
