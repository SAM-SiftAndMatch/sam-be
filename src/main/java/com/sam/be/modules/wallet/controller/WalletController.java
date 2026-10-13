package com.sam.be.modules.wallet.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.response.PageResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.wallet.dto.response.WalletLedgerEntryResponse;
import com.sam.be.modules.wallet.dto.response.WalletSummaryResponse;
import com.sam.be.modules.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FREELANCER')")
@Tag(name = "Sandbox Wallet", description = "Internal sandbox balance and ledger; no bank payout")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    @Operation(summary = "Get my sandbox wallet balance")
    public ApiResponse<WalletSummaryResponse> getMyWallet() {
        return ApiResponse.<WalletSummaryResponse>builder()
                .result(walletService.getMyWallet(SecurityUtils.getCurrentUserId()))
                .build();
    }

    @GetMapping("/me/transactions")
    @Operation(summary = "Get my sandbox wallet ledger")
    public ApiResponse<List<WalletLedgerEntryResponse>> getMyTransactions() {
        return ApiResponse.<List<WalletLedgerEntryResponse>>builder()
                .result(walletService.getMyLedger(SecurityUtils.getCurrentUserId()))
                .build();
    }

    @GetMapping("/me/transactions/page")
    @Operation(summary = "Get a page of my sandbox wallet ledger")
    public ApiResponse<PageResponse<WalletLedgerEntryResponse>> getMyTransactionsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.sam.be.common.exception.ApiException(
                    com.sam.be.common.exception.ErrorCode.VALIDATION_ERROR,
                    "page phải >= 0 và size phải từ 1 đến 100");
        }
        return ApiResponse.<PageResponse<WalletLedgerEntryResponse>>builder()
                .result(
                        PageResponse.from(
                                walletService.getMyLedgerPage(
                                        SecurityUtils.getCurrentUserId(), page, size)))
                .build();
    }
}
