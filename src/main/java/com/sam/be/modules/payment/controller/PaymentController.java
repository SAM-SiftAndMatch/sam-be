package com.sam.be.modules.payment.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.payment.dto.request.CreateEscrowRequest;
import com.sam.be.modules.payment.dto.response.PaymentResponse;
import com.sam.be.modules.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Escrow payments via VNPay")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/escrow")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Create escrow payment",
            description =
                    "Client creates a 50% escrow for an ACTIVE contract. Amount is computed by BE.")
    public ApiResponse<PaymentResponse> createEscrow(
            @Valid @RequestBody CreateEscrowRequest request) {
        PaymentResponse response =
                paymentService.createEscrow(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.<PaymentResponse>builder().result(response).build();
    }

    @PostMapping("/vnpay-ipn")
    @Operation(
            summary = "VNPay IPN webhook",
            description = "Server-to-server callback from VNPay, checksum verified, no JWT needed")
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paymentService.handleVnpayIpn(params));
    }

    @PostMapping("/{paymentId}/release")
    @PreAuthorize("@paymentAccessGuard.canRelease(#paymentId)")
    @Operation(
            summary = "Release escrow",
            description = "Only the contract client or Admin can release a held escrow")
    public ApiResponse<PaymentResponse> releaseEscrow(@PathVariable UUID paymentId) {
        PaymentResponse response =
                paymentService.releaseEscrow(SecurityUtils.getCurrentUserId(), paymentId);
        return ApiResponse.<PaymentResponse>builder().result(response).build();
    }

    @GetMapping("/contract/{contractId}")
    @Operation(
            summary = "List payments of a contract",
            description = "Only the contract client, freelancer or Admin can view")
    public ApiResponse<List<PaymentResponse>> getByContract(@PathVariable UUID contractId) {
        List<PaymentResponse> response =
                paymentService.getByContract(SecurityUtils.getCurrentUserId(), contractId);
        return ApiResponse.<List<PaymentResponse>>builder().result(response).build();
    }
}
