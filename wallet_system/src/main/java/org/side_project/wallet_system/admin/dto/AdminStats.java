package org.side_project.wallet_system.admin.dto;

import org.side_project.wallet_system.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Aggregate figures shown at the top of the admin dashboard.
 *
 * @param totalMembers  number of registered members
 * @param countByStatus transaction count per {@link TransactionStatus} (every status present, 0 when none)
 * @param totalDeposit  summed amount of COMPLETED deposits
 * @param totalWithdraw summed amount of COMPLETED withdrawals
 * @param totalBalance  summed balance held across every wallet
 */
public record AdminStats(
        long totalMembers,
        Map<TransactionStatus, Long> countByStatus,
        BigDecimal totalDeposit,
        BigDecimal totalWithdraw,
        BigDecimal totalBalance
) {}
