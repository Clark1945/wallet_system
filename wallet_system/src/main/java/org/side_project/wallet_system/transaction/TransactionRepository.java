package org.side_project.wallet_system.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    List<Transaction> findByStatusAndCreatedAtBefore(TransactionStatus status, Instant cutoff);

    Optional<Transaction> findByPaymentExternalId(String paymentExternalId);

    /**
     * Atomically transition a transaction's status only if it currently holds {@code from}.
     * The DB row lock makes this a compare-and-set: under concurrent callers (e.g. a webhook
     * delivered twice, or webhook racing the timeout job), exactly one call matches the row and
     * returns 1; the losers see the already-changed status and return 0. This is the single
     * source of truth for "who gets to run the side effect (credit/refund) exactly once".
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.status = :to WHERE t.id = :id AND t.status = :from")
    int compareAndSetStatus(@Param("id") UUID id,
                            @Param("from") TransactionStatus from,
                            @Param("to") TransactionStatus to);

    @Query("SELECT t FROM Transaction t WHERE t.fromWalletId = :walletId OR t.toWalletId = :walletId ORDER BY t.createdAt DESC")
    List<Transaction> findByWalletId(@Param("walletId") UUID walletId);

    @Query("SELECT t FROM Transaction t WHERE t.fromWalletId = :walletId OR t.toWalletId = :walletId ORDER BY t.createdAt DESC")
    Page<Transaction> findByWalletId(@Param("walletId") UUID walletId, Pageable pageable);
}
