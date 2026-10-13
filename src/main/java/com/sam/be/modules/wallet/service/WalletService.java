package com.sam.be.modules.wallet.service;

import com.sam.be.modules.wallet.dto.response.WalletLedgerEntryResponse;
import com.sam.be.modules.wallet.dto.response.WalletSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface WalletService {
    WalletSummaryResponse getMyWallet(UUID userId);

    List<WalletLedgerEntryResponse> getMyLedger(UUID userId);

    Page<WalletLedgerEntryResponse> getMyLedgerPage(UUID userId, int page, int size);
}
