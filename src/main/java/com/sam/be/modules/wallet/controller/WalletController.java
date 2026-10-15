package com.sam.be.modules.wallet.controller;

import com.sam.be.common.response.ApiResponse;
import com.sam.be.common.security.util.SecurityUtils;
import com.sam.be.modules.wallet.dto.response.WalletSummaryResponse;
import com.sam.be.modules.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
