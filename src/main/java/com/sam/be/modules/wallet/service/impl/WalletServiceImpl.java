package com.sam.be.modules.wallet.service.impl;

import com.sam.be.common.constant.enums.WalletType;
import com.sam.be.common.exception.ApiException;
import com.sam.be.common.exception.ErrorCode;
import com.sam.be.modules.user.entity.User;
import com.sam.be.modules.user.repository.UserRepository;
import com.sam.be.modules.wallet.dto.response.WalletLedgerEntryResponse;
import com.sam.be.modules.wallet.dto.response.WalletSummaryResponse;
import com.sam.be.modules.wallet.entity.WalletAccount;
import com.sam.be.modules.wallet.repository.WalletAccountRepository;
import com.sam.be.modules.wallet.repository.WalletLedgerEntryRepository;
import com.sam.be.modules.wallet.service.WalletService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletAccountRepository walletAccountRepository;
    private final WalletLedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public WalletSummaryResponse getMyWallet(UUID userId) {
        WalletAccount wallet = getOrCreateFreelancerWallet(userId);
        return toSummary(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletLedgerEntryResponse> getMyLedger(UUID userId) {
        return walletAccountRepository
                .findByOwnerId(userId)
                .map(
                        wallet ->
                                ledgerEntryRepository
                                        .findAllByWalletIdOrderByCreatedAtDesc(wallet.getId())
                                        .stream()
                                        .map(
                                                entry ->
                                                        WalletLedgerEntryResponse.builder()
                                                                .id(entry.getId())
                                                                .contractId(
                                                                        entry.getContract().getId())
                                                                .entryType(entry.getEntryType())
                                                                .amount(entry.getAmount())
                                                                .balanceAfter(
                                                                        entry.getBalanceAfter())
                                                                .description(entry.getDescription())
                                                                .createdAt(entry.getCreatedAt())
                                                                .build())
                                        .toList())
                .orElse(List.of());
    }

    WalletAccount getOrCreateFreelancerWallet(UUID userId) {
        return walletAccountRepository
                .findByOwnerId(userId)
                .orElseGet(
                        () -> {
                            User user =
                                    userRepository
                                            .findById(userId)
                                            .orElseThrow(
                                                    () ->
                                                            new ApiException(
                                                                    ErrorCode.RESOURCE_NOT_FOUND));
                            return walletAccountRepository.save(
                                    WalletAccount.builder()
                                            .owner(user)
                                            .walletKey("freelancer:" + userId)
                                            .walletType(WalletType.FREELANCER)
                                            .availableBalance(BigDecimal.ZERO)
                                            .build());
                        });
    }

    private WalletSummaryResponse toSummary(WalletAccount wallet) {
        return WalletSummaryResponse.builder()
                .walletId(wallet.getId())
                .currency(wallet.getCurrency())
                .availableBalance(wallet.getAvailableBalance())
                .sandbox(true)
                .build();
    }
}
