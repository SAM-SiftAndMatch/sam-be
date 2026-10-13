package com.sam.be.modules.wallet.repository;

import com.sam.be.modules.wallet.entity.WalletAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, UUID> {
    Optional<WalletAccount> findByOwnerId(UUID ownerId);

    Optional<WalletAccount> findByWalletKey(String walletKey);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletAccount w WHERE w.owner.id = :ownerId")
    Optional<WalletAccount> findByOwnerIdForUpdate(@Param("ownerId") UUID ownerId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletAccount w WHERE w.walletKey = :walletKey")
    Optional<WalletAccount> findByWalletKeyForUpdate(@Param("walletKey") String walletKey);
}
