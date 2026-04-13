package org.side_project.wallet_system.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.wallet.Wallet;
import org.side_project.wallet_system.wallet.WalletRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @InjectMocks private AuthService authService;

    @Test
    void register_newEmail_createsMemberAndWallet() {
        given(memberRepository.existsByEmail("new@test.com")).willReturn(false);
        given(passwordEncoder.encode("pass")).willReturn("hashed");
        Member saved = new Member();
        saved.setId(UUID.randomUUID());
        given(memberRepository.save(any())).willReturn(saved);

        Member result = authService.register("Alice", 25, "new@test.com", "pass");

        assertThat(result).isEqualTo(saved);
        then(walletRepository).should().save(any(Wallet.class));
    }

    @Test
    void register_duplicateEmail_throwsIllegalArgument() {
        given(memberRepository.existsByEmail("dup@test.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register("Bob", 30, "dup@test.com", "pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.email.duplicate");

        then(memberRepository).should(never()).save(any());
    }

    @Test
    void login_correctCredentials_returnsMember() {
        Member member = new Member();
        member.setPassword("hashed");
        given(memberRepository.findByEmail("user@test.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("pass", "hashed")).willReturn(true);

        Optional<Member> result = authService.login("user@test.com", "pass");

        assertThat(result).isPresent().contains(member);
    }

    @Test
    void login_wrongPassword_returnsEmpty() {
        Member member = new Member();
        member.setPassword("hashed");
        given(memberRepository.findByEmail("user@test.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        Optional<Member> result = authService.login("user@test.com", "wrong");

        assertThat(result).isEmpty();
    }

    @Test
    void login_unknownEmail_returnsEmpty() {
        given(memberRepository.findByEmail("none@test.com")).willReturn(Optional.empty());

        Optional<Member> result = authService.login("none@test.com", "pass");

        assertThat(result).isEmpty();
    }
}
