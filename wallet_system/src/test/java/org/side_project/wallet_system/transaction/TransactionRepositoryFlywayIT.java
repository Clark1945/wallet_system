package org.side_project.wallet_system.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test against a real PostgreSQL (Testcontainers) that runs the actual Flyway
 * migrations — unlike the H2-based unit/slice tests, which disable Flyway entirely.
 *
 * <p>It verifies three things our other tests cannot:
 * <ol>
 *   <li>All Flyway migrations (V1–V3) apply cleanly on a real Postgres.</li>
 *   <li>The JPA entities stay in sync with the migration schema ({@code ddl-auto=validate} —
 *       context startup fails on any drift).</li>
 *   <li>{@link TransactionSpec}'s dynamic Criteria predicates execute correctly against a real
 *       database (the H2 unit tests mock {@code findAll} and never exercise the predicate).</li>
 * </ol>
 *
 * <p>Auto-skipped when Docker is unavailable; runs in CI (where Docker is present).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("pgtest")
class TransactionRepositoryFlywayIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:14-alpine");

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID walletId;

    @BeforeEach
    void setUp() {
        walletId = UUID.randomUUID();
    }

    private Transaction save(TransactionType type, String amount, UUID toWallet) {
        Transaction t = new Transaction();
        t.setToWalletId(toWallet);
        t.setType(type);
        t.setAmount(new BigDecimal(amount));
        t.setStatus(TransactionStatus.COMPLETED);
        return transactionRepository.save(t);
    }

    @Test
    void flywayMigrationsApplyAndSchemaValidates() {
        // Reaching this point means: migrations applied AND entities validate against the schema.
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void transactionSpec_filtersByWalletAndType() {
        save(TransactionType.DEPOSIT, "100.00", walletId);
        save(TransactionType.WITHDRAW, "50.00", walletId);
        save(TransactionType.DEPOSIT, "999.00", UUID.randomUUID()); // different wallet

        List<Transaction> result = transactionRepository.findAll(
                TransactionSpec.filter(walletId, TransactionType.DEPOSIT, null, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void transactionSpec_filtersByDateRange() {
        save(TransactionType.DEPOSIT, "100.00", walletId); // created_at = now() via @PrePersist

        LocalDate today = LocalDate.now();
        assertThat(transactionRepository.findAll(
                TransactionSpec.filter(walletId, null, today, today))).hasSize(1);

        assertThat(transactionRepository.findAll(
                TransactionSpec.filter(walletId, null, today.plusDays(1), today.plusDays(2)))).isEmpty();
    }

    @Test
    void transactionSpec_matchesEitherFromOrToWallet() {
        save(TransactionType.TRANSFER, "30.00", walletId); // walletId as recipient
        Transaction sent = new Transaction();
        sent.setFromWalletId(walletId);                    // walletId as sender
        sent.setType(TransactionType.TRANSFER);
        sent.setAmount(new BigDecimal("20.00"));
        sent.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(sent);

        List<Transaction> result = transactionRepository.findAll(
                TransactionSpec.filter(walletId, null, null, null));

        assertThat(result).hasSize(2);
    }
}
