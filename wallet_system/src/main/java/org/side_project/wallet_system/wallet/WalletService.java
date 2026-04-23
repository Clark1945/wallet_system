package org.side_project.wallet_system.wallet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.transaction.Transaction;
import org.side_project.wallet_system.transaction.TransactionRepository;
import org.side_project.wallet_system.transaction.TransactionSpec;
import org.side_project.wallet_system.transaction.TransactionStatus;
import org.side_project.wallet_system.transaction.TransactionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final String DESC_DEPOSIT            = "Deposit";
    private static final String DESC_WITHDRAWAL         = "Withdrawal";
    private static final String DESC_WITHDRAWAL_TO_BANK = "Withdrawal to bank %s / %s";
    private static final String DESC_TRANSFER_TO        = "Transfer to %s";

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final HttpClient httpClient;

    @Value("${mock-bank.url}")
    private String mockBankUrl;

    @Value("${app.base-url}")
    private String appBaseUrl;

    public Wallet getWallet(UUID memberId) {
        return walletRepository.findByMemberId(memberId)
                .orElseThrow(() -> new WalletNotFoundException(memberId));
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

    // ── 同步儲值（向後相容，狀態直接 COMPLETED）────────────────────────────

    @Transactional
    public void deposit(UUID memberId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Deposit rejected - non-positive amount: memberId={}, amount={}", memberId, amount);
            throw new IllegalArgumentException("error.amount.positive");
        }
        Wallet wallet = walletRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new WalletNotFoundException(memberId));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        Transaction tx = new Transaction();
        tx.setToWalletId(wallet.getId());
        tx.setType(TransactionType.DEPOSIT);
        tx.setAmount(amount);
        tx.setDescription(DESC_DEPOSIT);
        tx.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(tx);
        log.info("Deposit: memberId={}, amount={}, newBalance={}", memberId, amount, wallet.getBalance());
    }

    // ── 非同步儲值（PENDING → COMPLETED / FAILED）────────────────────────────

    @Transactional
    public UUID initiateDeposit(UUID memberId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Deposit rejected - non-positive amount: memberId={}, amount={}", memberId, amount);
            throw new IllegalArgumentException("error.amount.positive");
        }
        Wallet wallet = getWallet(memberId);

        Transaction tx = new Transaction();
        tx.setToWalletId(wallet.getId());
        tx.setType(TransactionType.DEPOSIT);
        tx.setAmount(amount);
        tx.setDescription(DESC_DEPOSIT);
        tx.setStatus(TransactionStatus.PENDING);
        tx = transactionRepository.save(tx);
        log.info("Deposit initiated: memberId={}, amount={}, transactionId={}", memberId, amount, tx.getId());
        return tx.getId();
    }

    @Transactional
    public void completeDeposit(UUID transactionId) {
        transactionRepository.findById(transactionId).ifPresent(tx -> {
            if (tx.getStatus() == TransactionStatus.PENDING) {
                walletRepository.findByIdForUpdate(tx.getToWalletId()).ifPresent(wallet -> {
                    wallet.setBalance(wallet.getBalance().add(tx.getAmount()));
                    walletRepository.save(wallet);
                });
                tx.setStatus(TransactionStatus.COMPLETED);
                transactionRepository.save(tx);
                log.info("Deposit completed: transactionId={}, amount={}", transactionId, tx.getAmount());
            } else {
                log.warn("completeDeposit on non-PENDING: transactionId={}, currentStatus={}",
                        transactionId, tx.getStatus());
            }
        });
    }

    @Transactional
    public void linkPaymentExternalId(UUID transactionId, String externalId) {
        transactionRepository.findById(transactionId).ifPresent(tx -> {
            tx.setPaymentExternalId(externalId);
            transactionRepository.save(tx);
        });
    }

    @Transactional
    public boolean completeDepositByExternalId(String externalId) {
        return transactionRepository.findByPaymentExternalId(externalId)
                .map(tx -> { completeDeposit(tx.getId()); return true; })
                .orElse(false);
    }

    @Transactional
    public void failDeposit(UUID transactionId) {
        transactionRepository.findById(transactionId).ifPresent(tx -> {
            if (tx.getStatus() == TransactionStatus.PENDING) {
                tx.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(tx);
                log.warn("Deposit failed (timeout): transactionId={}, amount={}", transactionId, tx.getAmount());
            } else {
                log.warn("failDeposit on non-PENDING: transactionId={}, currentStatus={}",
                        transactionId, tx.getStatus());
            }
        });
    }

    // ── 同步提款（向後相容）──────────────────────────────────────────────────

    @Transactional
    public void withdraw(UUID memberId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Withdrawal rejected - non-positive amount: memberId={}, amount={}", memberId, amount);
            throw new IllegalArgumentException("error.amount.positive");
        }
        Wallet wallet = walletRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new WalletNotFoundException(memberId));
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
        tx.setDescription(DESC_WITHDRAWAL);
        tx.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(tx);
        log.info("Withdrawal: memberId={}, amount={}, newBalance={}", memberId, amount, wallet.getBalance());
    }

    // ── 非同步提款（REQUEST_COMPLETED → COMPLETED / FAILED）─────────────────

    @Transactional
    public void initiateWithdrawal(UUID memberId, BigDecimal amount, String bankCode, String bankAccount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Withdrawal rejected - non-positive amount: memberId={}, amount={}", memberId, amount);
            throw new IllegalArgumentException("error.amount.positive");
        }
        Wallet wallet = walletRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new WalletNotFoundException(memberId));
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
        tx.setDescription(String.format(DESC_WITHDRAWAL_TO_BANK, bankCode, bankAccount));
        tx.setStatus(TransactionStatus.REQUEST_COMPLETED);
        Transaction saved = transactionRepository.save(tx);

        String transactionId = saved.getId().toString();
        String callbackUrl = appBaseUrl + "/withdraw/webhook";
        String body = String.format(
            "{\"transactionId\":\"%s\",\"amount\":\"%s\",\"bankCode\":\"%s\",\"bankAccount\":\"%s\",\"callbackUrl\":\"%s\"}",
            transactionId, amount.toPlainString(), bankCode, bankAccount, callbackUrl);

        String traceId = MDC.get("traceId");
        CompletableFuture.runAsync(() -> {
            if (traceId != null) MDC.put("traceId", traceId);
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(mockBankUrl + "/api/withdraw"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
                if (traceId != null) requestBuilder.header("X-Trace-Id", traceId);
                httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                log.info("Withdrawal request sent to mock bank: transactionId={}", transactionId);
            } catch (Exception e) {
                log.error("Failed to send withdrawal to mock bank: transactionId={}", transactionId, e);
            } finally {
                MDC.remove("traceId");
            }
        });

        log.info("Withdrawal initiated: memberId={}, amount={}, transactionId={}", memberId, amount, transactionId);
    }

    @Transactional
    public void completeWithdrawal(UUID transactionId) {
        transactionRepository.findById(transactionId).ifPresent(tx -> {
            if (tx.getStatus() == TransactionStatus.REQUEST_COMPLETED) {
                tx.setStatus(TransactionStatus.COMPLETED);
                transactionRepository.save(tx);
                log.info("Withdrawal completed: transactionId={}, amount={}", transactionId, tx.getAmount());
            } else {
                log.warn("completeWithdrawal on non-REQUEST_COMPLETED: transactionId={}, currentStatus={}",
                        transactionId, tx.getStatus());
            }
        });
    }

    @Transactional
    public void failWithdrawal(UUID transactionId) {
        transactionRepository.findById(transactionId).ifPresent(tx -> {
            if (tx.getStatus() == TransactionStatus.REQUEST_COMPLETED) {
                tx.setStatus(TransactionStatus.FAILED);
                transactionRepository.save(tx);

                if (tx.getFromWalletId() != null) {
                    walletRepository.findByIdForUpdate(tx.getFromWalletId()).ifPresent(wallet -> {
                        wallet.setBalance(wallet.getBalance().add(tx.getAmount()));
                        walletRepository.save(wallet);
                        log.info("Withdrawal failed - balance refunded: transactionId={}, walletId={}, amount={}",
                                transactionId, tx.getFromWalletId(), tx.getAmount());
                    });
                }
            } else {
                log.warn("failWithdrawal on non-REQUEST_COMPLETED: transactionId={}, currentStatus={}",
                        transactionId, tx.getStatus());
            }
        });
    }

    // ── 轉帳（同步，直接 COMPLETED）─────────────────────────────────────────

    @Transactional
    public void transfer(UUID fromMemberId, String toWalletCode, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Transfer rejected - non-positive amount: fromMemberId={}, amount={}", fromMemberId, amount);
            throw new IllegalArgumentException("error.amount.positive");
        }
        // Unlocked reads for early validation (self-transfer, existence, quick balance check)
        Wallet fromWallet = walletRepository.findByMemberId(fromMemberId)
                .orElseThrow(() -> new WalletNotFoundException(fromMemberId));
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

        // Lock both wallets in ascending ID order to prevent deadlocks under concurrent transfers
        List<Wallet> locked = walletRepository.findByIdsForUpdate(
                List.of(fromWallet.getId(), toWallet.getId()));
        Wallet lockedFrom = locked.stream().filter(w -> w.getId().equals(fromWallet.getId())).findFirst()
                .orElseThrow(() -> new WalletNotFoundException(fromMemberId));
        Wallet lockedTo = locked.stream().filter(w -> w.getId().equals(toWallet.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("error.wallet.not.found"));

        // Re-check balance with the freshly locked values
        if (lockedFrom.getBalance().compareTo(amount) < 0) {
            log.warn("Transfer rejected - insufficient balance after lock: fromMemberId={}, amount={}, balance={}", fromMemberId, amount, lockedFrom.getBalance());
            throw new IllegalArgumentException("error.insufficient.balance");
        }

        lockedFrom.setBalance(lockedFrom.getBalance().subtract(amount));
        lockedTo.setBalance(lockedTo.getBalance().add(amount));
        walletRepository.save(lockedFrom);
        walletRepository.save(lockedTo);

        Transaction tx = new Transaction();
        tx.setFromWalletId(lockedFrom.getId());
        tx.setToWalletId(lockedTo.getId());
        tx.setType(TransactionType.TRANSFER);
        tx.setAmount(amount);
        tx.setDescription(String.format(DESC_TRANSFER_TO, toWalletCode));
        tx.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(tx);
        log.info("Transfer: fromMemberId={}, toWalletCode={}, amount={}", fromMemberId, toWalletCode, amount);
    }
}
