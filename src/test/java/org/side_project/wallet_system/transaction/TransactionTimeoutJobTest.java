package org.side_project.wallet_system.transaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.wallet.WalletService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionTimeoutJobTest {

    @Mock private WalletService walletService;
    @Mock private TransactionRepository transactionRepository;
    @InjectMocks private TransactionTimeoutJob job;

    private Transaction makeTx(TransactionStatus status) {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setAmount(new BigDecimal("100.00"));
        tx.setStatus(status);
        return tx;
    }

    @Test
    void job_expiresPendingDeposits_olderThan5min() {
        Transaction stale = makeTx(TransactionStatus.PENDING);
        given(transactionRepository.findByStatusAndCreatedAtBefore(eq(TransactionStatus.PENDING), any()))
                .willReturn(List.of(stale));
        given(transactionRepository.findByStatusAndCreatedAtBefore(eq(TransactionStatus.REQUEST_COMPLETED), any()))
                .willReturn(List.of());

        job.expireStaleTransactions();

        verify(walletService).failDeposit(stale.getId());
        verify(walletService, never()).failWithdrawal(any());
    }

    @Test
    void job_doesNotExpireRecentPendingDeposits() {
        given(transactionRepository.findByStatusAndCreatedAtBefore(eq(TransactionStatus.PENDING), any()))
                .willReturn(List.of());
        given(transactionRepository.findByStatusAndCreatedAtBefore(eq(TransactionStatus.REQUEST_COMPLETED), any()))
                .willReturn(List.of());

        job.expireStaleTransactions();

        verify(walletService, never()).failDeposit(any());
        verify(walletService, never()).failWithdrawal(any());
    }

    @Test
    void job_expiresRequestCompletedWithdrawals() {
        Transaction stale = makeTx(TransactionStatus.REQUEST_COMPLETED);
        given(transactionRepository.findByStatusAndCreatedAtBefore(eq(TransactionStatus.PENDING), any()))
                .willReturn(List.of());
        given(transactionRepository.findByStatusAndCreatedAtBefore(eq(TransactionStatus.REQUEST_COMPLETED), any()))
                .willReturn(List.of(stale));

        job.expireStaleTransactions();

        verify(walletService).failWithdrawal(stale.getId());
        verify(walletService, never()).failDeposit(any());
    }

    @Test
    void job_doesNothingWhenNoStaleTransactions() {
        given(transactionRepository.findByStatusAndCreatedAtBefore(any(), any(LocalDateTime.class)))
                .willReturn(List.of());

        job.expireStaleTransactions();

        verify(walletService, never()).failDeposit(any());
        verify(walletService, never()).failWithdrawal(any());
    }
}
