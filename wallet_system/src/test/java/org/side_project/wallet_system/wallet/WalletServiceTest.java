package org.side_project.wallet_system.wallet;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.notification.EmailPublisher;
import org.side_project.wallet_system.transaction.Transaction;
import org.side_project.wallet_system.transaction.TransactionRepository;
import org.side_project.wallet_system.transaction.TransactionStatus;
import org.side_project.wallet_system.transaction.TransactionType;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private MockBankClient mockBankClient;
    @Mock private EmailPublisher emailPublisher;
    @InjectMocks private WalletService walletService;

    private UUID memberId;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("1000.00"));
        wallet.setWalletCode("MyCode000001");
        // findByMemberId — used in transfer() early validation (unlocked)
        lenient().when(walletRepository.findByMemberId(memberId)).thenReturn(Optional.of(wallet));
        // findByMemberIdForUpdate — used in deposit/withdraw/initiateWithdrawal
        lenient().when(walletRepository.findByMemberIdForUpdate(memberId)).thenReturn(Optional.of(wallet));
        ReflectionTestUtils.setField(walletService, "appBaseUrl", "http://localhost:8080");
        // Wire self-reference so @Transactional proxy is reachable in async fallback tests
        ReflectionTestUtils.setField(walletService, "self", walletService);
    }

    // ── deposit ──────────────────────────────────────────────

    @Test
    void deposit_validAmount_increasesBalance() {
        walletService.deposit(memberId, new BigDecimal("500.00"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("1500.00");
        then(transactionRepository).should().save(argThat(tx ->
                tx.getType() == TransactionType.DEPOSIT
                && tx.getAmount().compareTo(new BigDecimal("500.00")) == 0));
    }

    @Test
    void deposit_zeroAmount_throws() {
        assertThatThrownBy(() -> walletService.deposit(memberId, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.amount.positive");
    }

    @Test
    void deposit_negativeAmount_throws() {
        assertThatThrownBy(() -> walletService.deposit(memberId, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── withdraw ─────────────────────────────────────────────

    @Test
    void withdraw_sufficientBalance_decreasesBalance() {
        walletService.withdraw(memberId, new BigDecimal("300.00"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("700.00");
        then(transactionRepository).should().save(argThat(tx ->
                tx.getType() == TransactionType.WITHDRAW));
    }

    @Test
    void withdraw_exactBalance_succeeds() {
        walletService.withdraw(memberId, new BigDecimal("1000.00"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void withdraw_insufficientBalance_throws() {
        assertThatThrownBy(() -> walletService.withdraw(memberId, new BigDecimal("2000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.insufficient.balance");

        assertThat(wallet.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void withdraw_zeroAmount_throws() {
        assertThatThrownBy(() -> walletService.withdraw(memberId, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── transfer ─────────────────────────────────────────────

    @Test
    void transfer_validRequest_movesBalanceBetweenWallets() {
        Wallet toWallet = new Wallet();
        toWallet.setId(UUID.randomUUID());
        toWallet.setBalance(BigDecimal.ZERO);
        toWallet.setWalletCode("ToCode000001");
        given(walletRepository.findByWalletCode("ToCode000001")).willReturn(Optional.of(toWallet));
        given(walletRepository.findByIdsForUpdate(any())).willReturn(List.of(wallet, toWallet));

        walletService.transfer(memberId, "ToCode000001", new BigDecimal("200.00"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("800.00");
        assertThat(toWallet.getBalance()).isEqualByComparingTo("200.00");
        then(transactionRepository).should().save(argThat(tx ->
                tx.getType() == TransactionType.TRANSFER
                && tx.getFromWalletId().equals(wallet.getId())
                && tx.getToWalletId().equals(toWallet.getId())));
    }

    @Test
    void transfer_selfTransfer_throws() {
        assertThatThrownBy(() -> walletService.transfer(memberId, "MyCode000001", new BigDecimal("100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.self.transfer");
    }

    @Test
    void transfer_invalidWalletCode_throws() {
        given(walletRepository.findByWalletCode("NotExist001x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.transfer(memberId, "NotExist001x", new BigDecimal("100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.wallet.not.found");
    }

    @Test
    void transfer_insufficientBalance_throws() {
        assertThatThrownBy(() -> walletService.transfer(memberId, "ToCode000001", new BigDecimal("9999")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.insufficient.balance");
    }

    @Test
    void transfer_zeroAmount_throws() {
        assertThatThrownBy(() -> walletService.transfer(memberId, "ToCode000001", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── status set by sync operations ────────────────────────────

    @Test
    void deposit_setsStatusCompleted() {
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        walletService.deposit(memberId, new BigDecimal("100.00"));

        then(transactionRepository).should().save(argThat(tx ->
                tx.getStatus() == TransactionStatus.COMPLETED));
    }

    @Test
    void withdraw_setsStatusCompleted() {
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        walletService.withdraw(memberId, new BigDecimal("100.00"));

        then(transactionRepository).should().save(argThat(tx ->
                tx.getStatus() == TransactionStatus.COMPLETED));
    }

    @Test
    void transfer_setsStatusCompleted() {
        Wallet toWallet = new Wallet();
        toWallet.setId(UUID.randomUUID());
        toWallet.setBalance(BigDecimal.ZERO);
        toWallet.setWalletCode("ToCode000001");
        given(walletRepository.findByWalletCode("ToCode000001")).willReturn(Optional.of(toWallet));
        given(walletRepository.findByIdsForUpdate(any())).willReturn(List.of(wallet, toWallet));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        walletService.transfer(memberId, "ToCode000001", new BigDecimal("100.00"));

        then(transactionRepository).should().save(argThat(tx ->
                tx.getStatus() == TransactionStatus.COMPLETED));
    }

    // ── initiateDeposit ──────────────────────────────────────────

    @Test
    void initiateDeposit_valid_createsPendingTx() {
        Transaction saved = new Transaction();
        saved.setId(UUID.randomUUID());
        given(transactionRepository.save(any())).willReturn(saved);

        UUID txId = walletService.initiateDeposit(memberId, new BigDecimal("500.00"), null);

        assertThat(txId).isEqualTo(saved.getId());
        assertThat(wallet.getBalance()).isEqualByComparingTo("1000.00"); // balance unchanged
        then(transactionRepository).should().save(argThat(tx ->
                tx.getStatus() == TransactionStatus.PENDING
                && tx.getType() == TransactionType.DEPOSIT
                && tx.getAmount().compareTo(new BigDecimal("500.00")) == 0));
    }

    @Test
    void initiateDeposit_zero_throws() {
        assertThatThrownBy(() -> walletService.initiateDeposit(memberId, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.amount.positive");
    }

    // ── completeDeposit ──────────────────────────────────────────

    @Test
    void completeDeposit_pending_addsBalanceAndCompletes() {
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setToWalletId(wallet.getId());
        tx.setAmount(new BigDecimal("200.00"));
        tx.setStatus(TransactionStatus.PENDING);
        given(transactionRepository.findById(txId)).willReturn(Optional.of(tx));
        given(walletRepository.findByIdForUpdate(wallet.getId())).willReturn(Optional.of(wallet));

        walletService.completeDeposit(txId);

        assertThat(wallet.getBalance()).isEqualByComparingTo("1200.00");
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void completeDeposit_nonPending_doesNothing() {
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setStatus(TransactionStatus.COMPLETED);
        given(transactionRepository.findById(txId)).willReturn(Optional.of(tx));

        walletService.completeDeposit(txId);

        assertThat(wallet.getBalance()).isEqualByComparingTo("1000.00");
        then(walletRepository).should(never()).save(any());
    }

    // ── failDeposit ──────────────────────────────────────────────

    @Test
    void failDeposit_pending_setsFailed_noRefund() {
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setAmount(new BigDecimal("300.00"));
        tx.setStatus(TransactionStatus.PENDING);
        given(transactionRepository.findById(txId)).willReturn(Optional.of(tx));

        walletService.failDeposit(txId);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(wallet.getBalance()).isEqualByComparingTo("1000.00"); // no refund — balance was never added
        then(walletRepository).should(never()).save(any());
    }

    // ── initiateWithdrawal ───────────────────────────────────────

    @Test
    void initiateWithdrawal_valid_createsRequestCompletedTx() {
        Transaction saved = new Transaction();
        saved.setId(UUID.randomUUID());
        given(transactionRepository.save(any())).willReturn(saved);

        walletService.initiateWithdrawal(memberId, new BigDecimal("400.00"), "012", "1234567890", null);

        assertThat(wallet.getBalance()).isEqualByComparingTo("600.00");
        then(transactionRepository).should().save(argThat(tx ->
                tx.getStatus() == TransactionStatus.REQUEST_COMPLETED
                && tx.getType() == TransactionType.WITHDRAW));
    }

    // ── circuit breaker — mock-bank fallback ─────────────────

    @Test
    void initiateWithdrawal_circuitOpen_immediatelyRefundsBalance() throws Exception {
        Transaction saved = new Transaction();
        saved.setId(UUID.randomUUID());
        saved.setStatus(TransactionStatus.REQUEST_COMPLETED);
        saved.setFromWalletId(wallet.getId());
        saved.setAmount(new BigDecimal("400.00"));
        given(transactionRepository.save(any())).willReturn(saved);
        given(transactionRepository.findById(saved.getId())).willReturn(Optional.of(saved));
        given(walletRepository.findByIdForUpdate(wallet.getId())).willReturn(Optional.of(wallet));
        willThrow(new MockBankUnavailableException("circuit open"))
            .given(mockBankClient).sendWithdrawRequest(any(), any(), any(), any(), any(), any());

        walletService.initiateWithdrawal(memberId, new BigDecimal("400.00"), "012", "1234567890", null);

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(saved.getStatus()).isEqualTo(TransactionStatus.FAILED);
            assertThat(wallet.getBalance()).isEqualByComparingTo("1000.00"); // refunded back to original
        });
    }

    @Test
    void initiateWithdrawal_mockBankNetworkError_staysRequestCompleted() throws Exception {
        Transaction saved = new Transaction();
        saved.setId(UUID.randomUUID());
        saved.setStatus(TransactionStatus.REQUEST_COMPLETED);
        given(transactionRepository.save(any())).willReturn(saved);
        willThrow(new RuntimeException("connection refused"))
            .given(mockBankClient).sendWithdrawRequest(any(), any(), any(), any(), any(), any());

        walletService.initiateWithdrawal(memberId, new BigDecimal("400.00"), "012", "1234567890", null);

        // Network error (non-CB): transaction stays REQUEST_COMPLETED, TimeoutJob refunds later
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
            assertThat(saved.getStatus()).isEqualTo(TransactionStatus.REQUEST_COMPLETED));
        then(transactionRepository).should(never()).save(argThat(tx ->
            tx.getStatus() == TransactionStatus.FAILED));
    }

    // ── email notification ────────────────────────────────────

    @Test
    void completeDeposit_withNotifyEmail_sendsEmail() {
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setToWalletId(wallet.getId());
        tx.setAmount(new BigDecimal("200.00"));
        tx.setStatus(TransactionStatus.PENDING);
        tx.setNotifyEmail("user@example.com");
        given(transactionRepository.findById(txId)).willReturn(Optional.of(tx));
        given(walletRepository.findByIdForUpdate(wallet.getId())).willReturn(Optional.of(wallet));

        walletService.completeDeposit(txId);

        then(emailPublisher).should().sendDepositSuccess("user@example.com", new BigDecimal("200.00"));
    }

    @Test
    void completeDeposit_withoutNotifyEmail_doesNotSendEmail() {
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setToWalletId(wallet.getId());
        tx.setAmount(new BigDecimal("200.00"));
        tx.setStatus(TransactionStatus.PENDING);
        given(transactionRepository.findById(txId)).willReturn(Optional.of(tx));
        given(walletRepository.findByIdForUpdate(wallet.getId())).willReturn(Optional.of(wallet));

        walletService.completeDeposit(txId);

        then(emailPublisher).shouldHaveNoInteractions();
    }

    @Test
    void completeWithdrawal_withNotifyEmail_sendsEmail() {
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setAmount(new BigDecimal("150.00"));
        tx.setStatus(TransactionStatus.REQUEST_COMPLETED);
        tx.setNotifyEmail("user@example.com");
        given(transactionRepository.findById(txId)).willReturn(Optional.of(tx));

        walletService.completeWithdrawal(txId);

        then(emailPublisher).should().sendWithdrawalSuccess("user@example.com", new BigDecimal("150.00"));
    }

    @Test
    void completeWithdrawal_withoutNotifyEmail_doesNotSendEmail() {
        UUID txId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setAmount(new BigDecimal("150.00"));
        tx.setStatus(TransactionStatus.REQUEST_COMPLETED);
        given(transactionRepository.findById(txId)).willReturn(Optional.of(tx));

        walletService.completeWithdrawal(txId);

        then(emailPublisher).shouldHaveNoInteractions();
    }
}
