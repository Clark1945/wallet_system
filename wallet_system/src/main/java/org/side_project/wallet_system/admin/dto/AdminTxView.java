package org.side_project.wallet_system.admin.dto;

import org.side_project.wallet_system.transaction.TransactionStatus;
import org.side_project.wallet_system.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single row in the admin transaction table. The {@code actor} is the member who owns the
 * wallet that initiated the transaction (recipient for deposits, sender otherwise); {@code name}
 * is that member's display name. Both are {@code null}/{@code "—"} when the wallet or member can
 * no longer be resolved.
 */
public record AdminTxView(
        String name,
        UUID actorId,
        UUID transactionId,
        TransactionStatus status,
        TransactionType type,
        BigDecimal amount,
        LocalDateTime time
) {}
