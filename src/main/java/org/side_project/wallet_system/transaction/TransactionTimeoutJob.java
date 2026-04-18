package org.side_project.wallet_system.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.wallet.WalletService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionTimeoutJob {

    private final WalletService walletService;
    private final TransactionRepository transactionRepository;

    @Scheduled(fixedDelay = 60_000)
    public void expireStaleTransactions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);

        List<Transaction> stalePending =
                transactionRepository.findByStatusAndCreatedAtBefore(TransactionStatus.PENDING, cutoff);
        if (!stalePending.isEmpty()) {
            log.info("TransactionTimeoutJob: found {} stale PENDING deposits", stalePending.size());
        }
        stalePending.forEach(tx -> {
            walletService.failDeposit(tx.getId());
            log.info("TransactionTimeoutJob: PENDING deposit expired, transactionId={}, amount={}",
                    tx.getId(), tx.getAmount());
        });

        List<Transaction> staleRequested =
                transactionRepository.findByStatusAndCreatedAtBefore(TransactionStatus.REQUEST_COMPLETED, cutoff);
        if (!staleRequested.isEmpty()) {
            log.info("TransactionTimeoutJob: found {} stale REQUEST_COMPLETED withdrawals", staleRequested.size());
        }
        staleRequested.forEach(tx -> {
            walletService.failWithdrawal(tx.getId());
            log.info("TransactionTimeoutJob: REQUEST_COMPLETED withdrawal expired, transactionId={}, amount={}",
                    tx.getId(), tx.getAmount());
        });
    }
}
