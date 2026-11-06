package com.sam.be.modules.payment.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.ai.dto.response.AiAmountVerification;
import com.sam.be.modules.payment.dto.request.ConfirmFundingRequest;
import com.sam.be.modules.payment.dto.request.CreateEscrowRequest;
import com.sam.be.modules.payment.dto.response.FreelancerEscrowSummaryResponse;
import com.sam.be.modules.payment.dto.response.FundingStatusResponse;
import com.sam.be.modules.payment.dto.response.PaymentResponse;
import com.sam.be.modules.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(
        name = "Payment",
        description = "Verified funding via Pay2S; settlement and seven-day maintenance hold")
public class PaymentController {

    private final PaymentService paymentService;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final com.sam.be.modules.payment.service.Pay2sIpnService ipn;

    @PostMapping("/fund")
    @com.sam.be.common.security.annotation.RateLimit(
            type = com.sam.be.common.security.annotation.RateLimit.Type.USER_ID,
            action = "contract_fund",
            limit = 5,
            duration = 60,
            failClosed = true)
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(
            summary = "Client funds 100% of contract value",
            description =
                    "Creates a one-time provider payment for the full agreed amount. AI amount check must pass.")
    public ApiResponse<PaymentResponse> createFund(
            @Valid @RequestBody CreateEscrowRequest request) {
        PaymentResponse response =
                paymentService.createContractFund(SecurityUtils.getCurrentUserId(), request);
        return ApiResponse.<PaymentResponse>builder().result(response).build();
    }

    @PostMapping("/deposit")
    @com.sam.be.common.security.annotation.RateLimit(
            type = com.sam.be.common.security.annotation.RateLimit.Type.USER_ID,
            action = "contract_deposit",
            limit = 5,
            duration = 60,
            failClosed = true)
    @PreAuthorize("hasRole('FREELANCER')")
    @Operation(
            summary = "Freelancer deposits 2% commitment bond",
            description =
                    "One-time provider payment of 2% of agreed amount. Refunded on success, paid to client on failure.")
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
            summary = "Read funding state after a payment redirect",
            description =
                    "Read-only compatibility endpoint. Only verified provider IPN can confirm funding.")
    public ApiResponse<PaymentResponse> confirmFunding(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) ConfirmFundingRequest request) {
        PaymentResponse response =
                paymentService.confirmFunding(SecurityUtils.getCurrentUserId(), paymentId, request);
        return ApiResponse.<PaymentResponse>builder().result(response).build();
    }

    @PostMapping("/pay2s-ipn")
    public java.util.Map<String, Boolean> pay2sIpn(@RequestBody String payload) {
        if (payload.length() > 65536)
            throw new com.sam.be.common.exception.ApiException(
                    com.sam.be.common.exception.ErrorCode.MALFORMED_JSON);
        try {
            // Parse explicitly: Boot 4 may select Jackson 3 for MVC while this module uses Jackson
            // 2.
            var parser =
                    json.reader()
                            .with(
                                    com.fasterxml.jackson.core.JsonParser.Feature
                                            .STRICT_DUPLICATE_DETECTION);
            return ipn.accept(parser.readTree(payload));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new com.sam.be.common.exception.ApiException(
                    com.sam.be.common.exception.ErrorCode.MALFORMED_JSON);
        }
    }

    @PostMapping("/{paymentId}/release")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Release escrow",
            description =
                    "Direct status-only release is disabled. Use settlement, maintenance and withdrawal workflows.")
    public ApiResponse<PaymentResponse> releaseEscrow(@PathVariable UUID paymentId) {
        PaymentResponse response =
                paymentService.releaseEscrow(SecurityUtils.getCurrentUserId(), paymentId);
        return ApiResponse.<PaymentResponse>builder().result(response).build();
    }

    @GetMapping("/freelancers/{freelancerId}/escrow-summary")
    @PreAuthorize("hasRole('FREELANCER') or hasRole('ADMIN')")
    @Operation(
            summary = "Get a freelancer escrow summary",
            description = "Shows net payout held, locked deposit, released amount and pending fee")
    public ApiResponse<FreelancerEscrowSummaryResponse> getFreelancerEscrowSummary(
            @PathVariable UUID freelancerId) {
        return ApiResponse.<FreelancerEscrowSummaryResponse>builder()
                .result(
                        paymentService.getFreelancerEscrowSummary(
                                SecurityUtils.getCurrentUserId(), freelancerId))
                .build();
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
