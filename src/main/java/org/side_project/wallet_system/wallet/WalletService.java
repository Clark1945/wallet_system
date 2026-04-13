package org.side_project.wallet_system.wallet;

import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.payment.Transaction;
import org.side_project.wallet_system.payment.TransactionRepository;
import org.side_project.wallet_system.payment.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public Wallet getWallet(UUID memberId) {
        return walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new RuntimeException("找不到錢包"));
    }

    public List<Transaction> getTransactions(UUID memberId) {
        Wallet wallet = getWallet(memberId);
        return transactionRepository.findByWalletId(wallet.getId());
    }

    @Transactional
    public void deposit(UUID memberId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金額必須大於 0");
        }
        Wallet wallet = getWallet(memberId);
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        Transaction tx = new Transaction();
        tx.setToWalletId(wallet.getId());
        tx.setType(TransactionType.DEPOSIT);
        tx.setAmount(amount);
        tx.setDescription("存款");
        transactionRepository.save(tx);
    }

    @Transactional
    public void withdraw(UUID memberId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金額必須大於 0");
        }
        Wallet wallet = getWallet(memberId);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("餘額不足");
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        Transaction tx = new Transaction();
        tx.setFromWalletId(wallet.getId());
        tx.setType(TransactionType.WITHDRAW);
        tx.setAmount(amount);
        tx.setDescription("提款");
        transactionRepository.save(tx);
    }

    @Transactional
    public void transfer(UUID fromMemberId, String toWalletCode, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金額必須大於 0");
        }
        Wallet fromWallet = getWallet(fromMemberId);
        if (fromWallet.getWalletCode().equals(toWalletCode)) {
            throw new IllegalArgumentException("無法轉帳給自己");
        }
        if (fromWallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("餘額不足");
        }

        Wallet toWallet = walletRepository.findByWalletCode(toWalletCode)
                .orElseThrow(() -> new IllegalArgumentException("找不到此錢包代碼"));

        fromWallet.setBalance(fromWallet.getBalance().subtract(amount));
        toWallet.setBalance(toWallet.getBalance().add(amount));
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        Transaction tx = new Transaction();
        tx.setFromWalletId(fromWallet.getId());
        tx.setToWalletId(toWallet.getId());
        tx.setType(TransactionType.TRANSFER);
        tx.setAmount(amount);
        tx.setDescription("轉帳給 " + toWalletCode);
        transactionRepository.save(tx);
    }
}
