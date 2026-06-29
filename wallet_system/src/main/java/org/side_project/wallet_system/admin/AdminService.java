package org.side_project.wallet_system.admin;

import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.admin.dto.AdminStats;
import org.side_project.wallet_system.admin.dto.AdminTxView;
import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.transaction.Transaction;
import org.side_project.wallet_system.transaction.TransactionRepository;
import org.side_project.wallet_system.transaction.TransactionSpec;
import org.side_project.wallet_system.transaction.TransactionStatus;
import org.side_project.wallet_system.transaction.TransactionType;
import org.side_project.wallet_system.wallet.Wallet;
import org.side_project.wallet_system.wallet.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only queries backing the admin金流控管 dashboard. Aggregates platform-wide stats and
 * resolves cross-wallet transactions into display rows ({@link AdminTxView}).
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final MemberRepository memberRepository;

    /** Platform-wide aggregate figures for the dashboard header. */
    public AdminStats getStats() {
        Map<TransactionStatus, Long> countByStatus = new EnumMap<>(TransactionStatus.class);
        for (TransactionStatus s : TransactionStatus.values()) {
            countByStatus.put(s, 0L);
        }
        for (Object[] row : transactionRepository.countGroupedByStatus()) {
            countByStatus.put((TransactionStatus) row[0], (Long) row[1]);
        }
        return new AdminStats(
                memberRepository.count(),
                countByStatus,
                transactionRepository.sumCompletedAmountByType(TransactionType.DEPOSIT),
                transactionRepository.sumCompletedAmountByType(TransactionType.WITHDRAW),
                walletRepository.sumAllBalances()
        );
    }

    /**
     * One page of transactions across all wallets, filtered by status / order number / date range,
     * each enriched with the initiating member's name and id.
     */
    @Transactional(readOnly = true)
    public Page<AdminTxView> queryTransactions(TransactionStatus status,
                                               String orderNo,
                                               LocalDate startDate,
                                               LocalDate endDate,
                                               int page, int size) {
        Page<Transaction> txPage = transactionRepository.findAll(
                TransactionSpec.adminFilter(status, orderNo, startDate, endDate),
                PageRequest.of(page, size));

        // Resolve the actor wallet of each row (deposit → recipient, otherwise → sender) to a member
        // in a single lookup, avoiding an N+1 query per row.
        List<UUID> walletIds = txPage.getContent().stream()
                .map(AdminService::actorWalletId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Member> membersByWallet = walletRepository.findAllById(walletIds).stream()
                .filter(w -> w.getMember() != null)
                .collect(Collectors.toMap(Wallet::getId, Wallet::getMember, (a, b) -> a));

        return txPage.map(tx -> {
            Member actor = membersByWallet.get(actorWalletId(tx));
            return new AdminTxView(
                    actor != null ? actor.getName() : null,
                    actor != null ? actor.getId() : null,
                    tx.getId(),
                    tx.getStatus(),
                    tx.getType(),
                    tx.getAmount(),
                    tx.getCreatedAtLocal());
        });
    }

    /** The wallet whose owner is considered the actor: recipient for deposits, sender otherwise. */
    private static UUID actorWalletId(Transaction tx) {
        return tx.getType() == TransactionType.DEPOSIT ? tx.getToWalletId() : tx.getFromWalletId();
    }
}
