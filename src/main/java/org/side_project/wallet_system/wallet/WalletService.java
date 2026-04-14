package org.side_project.wallet_system.wallet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.payment.Transaction;
import org.side_project.wallet_system.payment.TransactionRepository;
import org.side_project.wallet_system.payment.TransactionType;
import org.side_project.wallet_system.payment.TransactionSpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public Wallet getWallet(UUID memberId) {
        return walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
    }

    public List<Transaction> getTransactions(UUID memberId) {
        Wallet wallet = getWallet(memberId);
        return transactionRepository.findByWalletId(wallet.getId());
    }

    public Page<Transaction> getTransactions(UUID memberId, int page, int size) {
        Wallet wallet = getWallet(memberId);
        return transactionRepository.findByWalletId(wallet.getId(), PageRequest.of(page, size));
    }

    public Page<Transaction> getTransactions(UUID memberId,
                                             TransactionType type,
                                             LocalDate startDate,
                                             LocalDate endDate,
                                             int page, int size) {
        Wallet wallet = getWallet(memberId);
        return transactionRepository.findAll(
            TransactionSpec.filter(wallet.getId(), type, startDate, endDate),
            PageRequest.of(page, size)
        );
    }

    @Transactional
    public void deposit(UUID memberId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Deposit rejected - non-positive amount: memberId={}, amount={}", memberId, amount);
            throw new IllegalArgumentException("error.amount.positive");
        }
        Wallet wallet = getWallet(memberId);
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        Transaction tx = new Transaction();
        tx.setToWalletId(wallet.getId());
        tx.setType(TransactionType.DEPOSIT);
        tx.setAmount(amount);
        tx.setDescription("Deposit");
        transactionRepository.save(tx);
        log.info("Deposit: memberId={}, amount={}, newBalance={}", memberId, amount, wallet.getBalance());
    }

    @Transactional
    public void withdraw(UUID memberId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Withdrawal rejected - non-positive amount: memberId={}, amount={}", memberId, amount);
            throw new IllegalArgumentException("error.amount.positive");
        }
        Wallet wallet = getWallet(memberId);
        if (wallet.getBalance().compareTo(amount) < 0) {
            log.warn("Withdrawal rejected - insufficient balance: memberId={}, amount={}, balance={}", memberId, amount, wallet.getBalance());
            throw new IllegalArgumentException("error.insufficient.balance");
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        Transaction tx = new Transaction();
        tx.setFromWalletId(wallet.getId());
        tx.setType(TransactionType.WITHDRAW);
        tx.setAmount(amount);
        tx.setDescription("Withdrawal");
        transactionRepository.save(tx);
        log.info("Withdrawal: memberId={}, amount={}, newBalance={}", memberId, amount, wallet.getBalance());
    }

    @Transactional
    public void transfer(UUID fromMemberId, String toWalletCode, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Transfer rejected - non-positive amount: fromMemberId={}, amount={}", fromMemberId, amount);
            throw new IllegalArgumentException("error.amount.positive");
        }
        Wallet fromWallet = getWallet(fromMemberId);
        if (fromWallet.getWalletCode().equals(toWalletCode)) {
            log.warn("Transfer rejected - self-transfer: memberId={}", fromMemberId);
            throw new IllegalArgumentException("error.self.transfer");
        }
        if (fromWallet.getBalance().compareTo(amount) < 0) {
            log.warn("Transfer rejected - insufficient balance: fromMemberId={}, amount={}, balance={}", fromMemberId, amount, fromWallet.getBalance());
            throw new IllegalArgumentException("error.insufficient.balance");
        }

        Wallet toWallet = walletRepository.findByWalletCode(toWalletCode)
                .orElseThrow(() -> {
                    log.warn("Transfer rejected - wallet not found: toWalletCode={}", toWalletCode);
                    return new IllegalArgumentException("error.wallet.not.found");
                });

        fromWallet.setBalance(fromWallet.getBalance().subtract(amount));
        toWallet.setBalance(toWallet.getBalance().add(amount));
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        Transaction tx = new Transaction();
        tx.setFromWalletId(fromWallet.getId());
        tx.setToWalletId(toWallet.getId());
        tx.setType(TransactionType.TRANSFER);
        tx.setAmount(amount);
        tx.setDescription("Transfer to " + toWalletCode);
        transactionRepository.save(tx);
        log.info("Transfer: fromMemberId={}, toWalletCode={}, amount={}", fromMemberId, toWalletCode, amount);
    }
}
