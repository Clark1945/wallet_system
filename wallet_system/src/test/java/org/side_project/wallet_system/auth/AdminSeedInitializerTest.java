package org.side_project.wallet_system.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSeedInitializerTest {

    @Mock MemberRepository memberRepository;
    @InjectMocks AdminSeedInitializer initializer;

    private void setEmail(String email) {
        ReflectionTestUtils.setField(initializer, "adminEmail", email);
    }

    @Test
    void seedAdmin_memberNotAdmin_promotesAndSaves() {
        setEmail("admin@example.com");
        Member member = new Member();
        member.setEmail("admin@example.com");
        member.setAdmin(false);
        given(memberRepository.findByEmail("admin@example.com")).willReturn(Optional.of(member));

        initializer.seedAdmin();

        assertThat(member.isAdmin()).isTrue();
        then(memberRepository).should().save(member);
    }

    @Test
    void seedAdmin_alreadyAdmin_doesNotSave() {
        setEmail("admin@example.com");
        Member member = new Member();
        member.setAdmin(true);
        given(memberRepository.findByEmail("admin@example.com")).willReturn(Optional.of(member));

        initializer.seedAdmin();

        then(memberRepository).should(never()).save(any());
    }

    @Test
    void seedAdmin_memberNotFound_isNoOp() {
        setEmail("missing@example.com");
        given(memberRepository.findByEmail("missing@example.com")).willReturn(Optional.empty());

        initializer.seedAdmin();

        then(memberRepository).should(never()).save(any());
    }

    @Test
    void seedAdmin_blankEmail_skipsLookup() {
        setEmail("");

        initializer.seedAdmin();

        then(memberRepository).shouldHaveNoInteractions();
    }
}
