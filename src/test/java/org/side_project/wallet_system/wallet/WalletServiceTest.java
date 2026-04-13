package org.side_project.wallet_system.wallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.payment.Transaction;
import org.side_project.wallet_system.payment.TransactionRepository;
import org.side_project.wallet_system.payment.TransactionType;

import java.math.BigDecimal;
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
        lenient().when(walletRepository.findByMemberId(memberId)).thenReturn(Optional.of(wallet));
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
}
