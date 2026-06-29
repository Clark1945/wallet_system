package org.side_project.wallet_system.transaction;

import org.junit.jupiter.api.Test;
import org.side_project.wallet_system.config.AppZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link TransactionSpec#adminFilter} predicates against H2 (test profile, Flyway off).
 * Unlike the per-wallet {@code filter}, {@code adminFilter} spans every wallet and adds status /
 * order-number matching.
 */
@DataJpaTest
@ActiveProfiles("test")
class AdminTransactionSpecTest {

    @Autowired
    private TransactionRepository transactionRepository;

    private Transaction save(TransactionType type, String amount, TransactionStatus status, String externalId) {
        Transaction t = new Transaction();
        t.setToWalletId(UUID.randomUUID());
        t.setType(type);
        t.setAmount(new BigDecimal(amount));
        t.setStatus(status);
        t.setPaymentExternalId(externalId);
        return transactionRepository.save(t);
    }

    @Test
    void adminFilter_spansAllWallets_noStatusOrOrderNo() {
        save(TransactionType.DEPOSIT, "100.00", TransactionStatus.COMPLETED, null);
        save(TransactionType.WITHDRAW, "50.00", TransactionStatus.PENDING, null);

        Page<Transaction> page = transactionRepository.findAll(
                TransactionSpec.adminFilter(null, null, null, null), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void adminFilter_filtersByStatus() {
        save(TransactionType.DEPOSIT, "100.00", TransactionStatus.COMPLETED, null);
        save(TransactionType.WITHDRAW, "50.00", TransactionStatus.FAILED, null);

        Page<Transaction> page = transactionRepository.findAll(
                TransactionSpec.adminFilter(TransactionStatus.FAILED, null, null, null), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void adminFilter_orderNo_matchesTransactionUuidExactly() {
        Transaction target = save(TransactionType.DEPOSIT, "100.00", TransactionStatus.COMPLETED, null);
        save(TransactionType.DEPOSIT, "200.00", TransactionStatus.COMPLETED, null);

        Page<Transaction> page = transactionRepository.findAll(
                TransactionSpec.adminFilter(null, target.getId().toString(), null, null), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(target.getId());
    }

    @Test
    void adminFilter_orderNo_matchesPaymentExternalIdSubstring() {
        save(TransactionType.DEPOSIT, "100.00", TransactionStatus.COMPLETED, "stripe_pi_ABC123");
        save(TransactionType.DEPOSIT, "200.00", TransactionStatus.COMPLETED, "sbps_XYZ789");

        Page<Transaction> page = transactionRepository.findAll(
                TransactionSpec.adminFilter(null, "ABC123", null, null), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getPaymentExternalId()).isEqualTo("stripe_pi_ABC123");
    }

    @Test
    void adminFilter_orderNo_nonUuidNonMatching_returnsEmpty() {
        save(TransactionType.DEPOSIT, "100.00", TransactionStatus.COMPLETED, "stripe_pi_ABC123");

        Page<Transaction> page = transactionRepository.findAll(
                TransactionSpec.adminFilter(null, "no-such-order", null, null), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void adminFilter_filtersByDateRange() {
        save(TransactionType.DEPOSIT, "100.00", TransactionStatus.COMPLETED, null);

        LocalDate today = LocalDate.now(AppZone.DISPLAY);
        assertThat(transactionRepository.findAll(
                TransactionSpec.adminFilter(null, null, today, today), PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);

        assertThat(transactionRepository.findAll(
                TransactionSpec.adminFilter(null, null, today.plusDays(1), today.plusDays(2)), PageRequest.of(0, 10))
                .getTotalElements()).isZero();
    }

    @Test
    void adminFilter_paginates_tenPerPage() {
        for (int i = 0; i < 12; i++) {
            save(TransactionType.DEPOSIT, "10.00", TransactionStatus.COMPLETED, "ext-" + i);
        }

        Page<Transaction> firstPage = transactionRepository.findAll(
                TransactionSpec.adminFilter(null, null, null, null), PageRequest.of(0, 10));

        assertThat(firstPage.getTotalElements()).isEqualTo(12);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(10);
    }
}
