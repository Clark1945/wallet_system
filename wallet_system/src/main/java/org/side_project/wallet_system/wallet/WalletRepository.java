package org.side_project.wallet_system.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByMemberId(UUID memberId);
    Optional<Wallet> findByWalletCode(String walletCode);
}
