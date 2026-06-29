package org.side_project.wallet_system.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.admin.dto.AdminStats;
import org.side_project.wallet_system.admin.dto.AdminTxView;
import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.transaction.Transaction;
import org.side_project.wallet_system.transaction.TransactionRepository;
import org.side_project.wallet_system.transaction.TransactionStatus;
import org.side_project.wallet_system.transaction.TransactionType;
import org.side_project.wallet_system.wallet.Wallet;
import org.side_project.wallet_system.wallet.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private MemberRepository memberRepository;
    @InjectMocks private AdminService adminService;

    // ─── getStats ────────────────────────────────────────────────────────────────

    @Test
    void getStats_aggregatesAndFillsMissingStatusesWithZero() {
        given(memberRepository.count()).willReturn(7L);
        given(transactionRepository.countGroupedByStatus()).willReturn(List.of(
                new Object[]{TransactionStatus.COMPLETED, 4L},
                new Object[]{TransactionStatus.FAILED, 1L}
        ));
        given(transactionRepository.sumCompletedAmountByType(TransactionType.DEPOSIT))
                .willReturn(new BigDecimal("1500.00"));
        given(transactionRepository.sumCompletedAmountByType(TransactionType.WITHDRAW))
                .willReturn(new BigDecimal("300.00"));
        given(walletRepository.sumAllBalances()).willReturn(new BigDecimal("1200.00"));

        AdminStats stats = adminService.getStats();

        assertThat(stats.totalMembers()).isEqualTo(7L);
        assertThat(stats.totalDeposit()).isEqualByComparingTo("1500.00");
        assertThat(stats.totalWithdraw()).isEqualByComparingTo("300.00");
        assertThat(stats.totalBalance()).isEqualByComparingTo("1200.00");
        // every status present; the ones absent from the query default to 0
        assertThat(stats.countByStatus())
                .containsEntry(TransactionStatus.COMPLETED, 4L)
                .containsEntry(TransactionStatus.FAILED, 1L)
                .containsEntry(TransactionStatus.PENDING, 0L)
                .containsEntry(TransactionStatus.REQUEST_COMPLETED, 0L);
    }

    // ─── queryTransactions ───────────────────────────────────────────────────────

    @Test
    void queryTransactions_resolvesActorNameAndId_perTransactionType() {
        UUID depositWallet  = UUID.randomUUID();
        UUID withdrawWallet = UUID.randomUUID();
        UUID orphanWallet   = UUID.randomUUID();

        Transaction deposit  = tx(TransactionType.DEPOSIT,  null,           depositWallet);
        Transaction withdraw = tx(TransactionType.WITHDRAW, withdrawWallet, null);
        Transaction transfer = tx(TransactionType.TRANSFER, orphanWallet,   UUID.randomUUID());

        given(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(deposit, withdraw, transfer)));

        Member alice = member("Alice");
        Member bob   = member("Bob");
        // orphanWallet intentionally not returned → name/actorId stay null
        given(walletRepository.findAllById(any())).willReturn(List.of(
                wallet(depositWallet, alice),
                wallet(withdrawWallet, bob)
        ));

        Page<AdminTxView> result =
                adminService.queryTransactions(null, null, null, null, 0, 10);

        assertThat(result.getContent()).hasSize(3);

        AdminTxView depositRow = result.getContent().get(0);
        assertThat(depositRow.name()).isEqualTo("Alice");        // deposit → recipient (toWalletId)
        assertThat(depositRow.actorId()).isEqualTo(alice.getId());
        assertThat(depositRow.transactionId()).isEqualTo(deposit.getId());

        AdminTxView withdrawRow = result.getContent().get(1);
        assertThat(withdrawRow.name()).isEqualTo("Bob");         // withdraw → sender (fromWalletId)
        assertThat(withdrawRow.actorId()).isEqualTo(bob.getId());

        AdminTxView transferRow = result.getContent().get(2);
        assertThat(transferRow.name()).isNull();                 // owner unresolvable
        assertThat(transferRow.actorId()).isNull();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private static Transaction tx(TransactionType type, UUID fromWallet, UUID toWallet) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setType(type);
        t.setFromWalletId(fromWallet);
        t.setToWalletId(toWallet);
        t.setAmount(new BigDecimal("100.00"));
        t.setStatus(TransactionStatus.COMPLETED);
        return t;
    }

    private static Member member(String name) {
        Member m = new Member();
        m.setId(UUID.randomUUID());
        m.setName(name);
        return m;
    }

    private static Wallet wallet(UUID id, Member member) {
        Wallet w = new Wallet();
        w.setId(id);
        w.setMember(member);
        return w;
    }
}
