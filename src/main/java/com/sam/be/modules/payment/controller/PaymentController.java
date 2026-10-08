package com.sam.be.modules.payment.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.ai.dto.response.AiAmountVerification;
import com.sam.be.modules.payment.dto.request.ConfirmFundingRequest;
import com.sam.be.modules.payment.dto.request.CreateEscrowRequest;
import com.sam.be.modules.payment.dto.response.FundingStatusResponse;
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
@Tag(name = "Payment", description = "Funding before start: client 100% + freelancer 2% deposit via VNPay")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/fund")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Client funds 100% of contract value",
            description =
                    "Creates a one-time VNPay payment for the full agreed amount. AI amount check must pass.")
    public ApiResponse<PaymentResponse> createFund(
            @Valid @RequestBody CreateEscrowRequest request) {
        PaymentResponse response =
                paymentService.createContractFund(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.<PaymentResponse>builder().result(response).build();
    }

    @PostMapping("/deposit")
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(
            summary = "Freelancer deposits 2% commitment bond",
            description =
                    "One-time VNPay payment of 2% of agreed amount. Refunded on success, paid to client on failure.")
    public ApiResponse<PaymentResponse> createDeposit(
            @Valid @RequestBody CreateEscrowRequest request) {
        PaymentResponse response =
                paymentService.createSecurityDeposit(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.<PaymentResponse>builder().result(response).build();
    }

    @GetMapping("/funding/{contractId}")
    @Operation(
            summary = "Get funding status",
            description =
                    "Amounts each side must pay, what freelancer gets (90%) and platform fee (10%), plus paid states")
    public ApiResponse<FundingStatusResponse> getFundingStatus(@PathVariable UUID contractId) {
        FundingStatusResponse response =
                paymentService.getFundingStatus(SecurityUtils.getCurrentUserId(), contractId);
        return ApiResponse.<FundingStatusResponse>builder().result(response).build();
    }

    @PostMapping("/{paymentId}/confirm")
    @Operation(
            summary = "FE confirms a funding payment after VNPay redirect",
            description =
                    "Same workaround as subscription confirm-payment: FE reports success, BE cross-checks txnRef/amount then marks HELD. Idempotent with IPN.")
    public ApiResponse<PaymentResponse> confirmFunding(
            @PathVariable UUID paymentId, @RequestBody(required = false) ConfirmFundingRequest request) {
        PaymentResponse response =
                paymentService.confirmFunding(SecurityUtils.getCurrentUserId(), paymentId, request);
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

    @PostMapping("/contracts/{contractId}/verify-amount")
    @Operation(
            summary = "AI verifies contract amount",
            description =
                    "AI reads the contract text and checks it against the agreed amount. Mismatch blocks funding.")
    public ApiResponse<AiAmountVerification> verifyAmount(@PathVariable UUID contractId) {
        AiAmountVerification response =
                paymentService.verifyContractAmount(SecurityUtils.getCurrentUserId(), contractId);
        return ApiResponse.<AiAmountVerification>builder().result(response).build();
    }
}
