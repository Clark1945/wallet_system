package org.side_project.wallet_system.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.objects.MemberStatus;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.auth.service.AuthService;
import org.side_project.wallet_system.wallet.Wallet;
import org.side_project.wallet_system.wallet.WalletRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AuthService authService;

    // ─── initiateRegistration ────────────────────────────────────────────────────

    @Test
    void initiateRegistration_newEmail_createsPendingMemberWithoutWallet() {
        given(memberRepository.findByEmail("new@test.com")).willReturn(Optional.empty());
        given(passwordEncoder.encode("pass")).willReturn("hashed");
        Member saved = new Member();
        saved.setId(UUID.randomUUID());
        given(memberRepository.save(any())).willReturn(saved);

        Member result = authService.initiateRegistration("Alice", 25, "new@test.com", "pass");

        assertThat(result).isEqualTo(saved);
        then(walletRepository).should(never()).save(any()); // wallet not created until activation
    }

    @Test
    void initiateRegistration_activeDuplicateEmail_throwsIllegalArgument() {
        Member active = new Member();
        active.setStatus(MemberStatus.ACTIVE);
        given(memberRepository.findByEmail("dup@test.com")).willReturn(Optional.of(active));

        assertThatThrownBy(() -> authService.initiateRegistration("Bob", 30, "dup@test.com", "pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.email.duplicate");

        then(memberRepository).should(never()).save(any());
    }

    @Test
    void initiateRegistration_pendingDuplicateEmail_deletesAndRecreates() {
        Member pending = new Member();
        pending.setId(UUID.randomUUID());
        pending.setStatus(MemberStatus.PENDING);
        given(memberRepository.findByEmail("pending@test.com")).willReturn(Optional.of(pending));
        given(passwordEncoder.encode("pass")).willReturn("hashed");
        Member saved = new Member();
        saved.setId(UUID.randomUUID());
        given(memberRepository.save(any())).willReturn(saved);

        Member result = authService.initiateRegistration("Carol", 20, "pending@test.com", "pass");

        assertThat(result).isEqualTo(saved);
        then(memberRepository).should().delete(pending);
    }

    // ─── activateRegistration ────────────────────────────────────────────────────

    @Test
    void activateRegistration_pendingMember_setsActiveAndCreatesWallet() {
        UUID memberId = UUID.randomUUID();
        Member pending = new Member();
        pending.setId(memberId);
        pending.setStatus(MemberStatus.PENDING);
        given(memberRepository.findById(memberId)).willReturn(Optional.of(pending));

        authService.activateRegistration(memberId);

        assertThat(pending.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        then(memberRepository).should().save(pending);
        then(walletRepository).should().save(any(Wallet.class));
    }

    // ─── login ───────────────────────────────────────────────────────────────────

    @Test
    void login_correctCredentials_returnsMember() {
        Member member = new Member();
        member.setPassword("hashed");
        member.setStatus(MemberStatus.ACTIVE);
        given(memberRepository.findByEmail("user@test.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("pass", "hashed")).willReturn(true);

        Optional<Member> result = authService.login("user@test.com", "pass");

        assertThat(result).isPresent().contains(member);
    }

    @Test
    void login_wrongPassword_returnsEmpty() {
        Member member = new Member();
        member.setPassword("hashed");
        member.setStatus(MemberStatus.ACTIVE);
        given(memberRepository.findByEmail("user@test.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        Optional<Member> result = authService.login("user@test.com", "wrong");

        assertThat(result).isEmpty();
    }

    @Test
    void login_pendingMember_returnsEmpty() {
        Member member = new Member();
        member.setPassword("hashed");
        member.setStatus(MemberStatus.PENDING);
        given(memberRepository.findByEmail("pending@test.com")).willReturn(Optional.of(member));

        Optional<Member> result = authService.login("pending@test.com", "pass");

        assertThat(result).isEmpty();
        then(passwordEncoder).shouldHaveNoInteractions(); // PENDING status filtered before password check
    }

    @Test
    void login_unknownEmail_returnsEmpty() {
        given(memberRepository.findByEmail("none@test.com")).willReturn(Optional.empty());

        Optional<Member> result = authService.login("none@test.com", "pass");

        assertThat(result).isEmpty();
    }
}
