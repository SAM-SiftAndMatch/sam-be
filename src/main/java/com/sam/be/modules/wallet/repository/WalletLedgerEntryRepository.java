package com.sam.be.modules.wallet.repository;

import com.sam.be.modules.wallet.entity.WalletLedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletLedgerEntryRepository extends JpaRepository<WalletLedgerEntry, UUID> {
    List<WalletLedgerEntry> findAllByWalletIdOrderByCreatedAtDesc(UUID walletId);

    Page<WalletLedgerEntry> findAllByWalletId(UUID walletId, Pageable pageable);
}
