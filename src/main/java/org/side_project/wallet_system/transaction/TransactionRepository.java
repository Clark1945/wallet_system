package org.side_project.wallet_system.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    List<Transaction> findByStatusAndCreatedAtBefore(TransactionStatus status, LocalDateTime cutoff);

    Optional<Transaction> findByPaymentExternalId(String paymentExternalId);

    @Query("SELECT t FROM Transaction t WHERE t.fromWalletId = :walletId OR t.toWalletId = :walletId ORDER BY t.createdAt DESC")
    List<Transaction> findByWalletId(@Param("walletId") UUID walletId);

    @Query("SELECT t FROM Transaction t WHERE t.fromWalletId = :walletId OR t.toWalletId = :walletId ORDER BY t.createdAt DESC")
    Page<Transaction> findByWalletId(@Param("walletId") UUID walletId, Pageable pageable);
}
