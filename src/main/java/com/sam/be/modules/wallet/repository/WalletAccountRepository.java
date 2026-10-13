package com.sam.be.modules.wallet.repository;

import com.sam.be.modules.wallet.entity.WalletAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, UUID> {
    Optional<WalletAccount> findByOwnerId(UUID ownerId);

    Optional<WalletAccount> findByWalletKey(String walletKey);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletAccount> findByOwnerIdForUpdate(UUID ownerId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletAccount> findByWalletKeyForUpdate(String walletKey);
}
