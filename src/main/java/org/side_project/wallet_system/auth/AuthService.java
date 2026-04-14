package org.side_project.wallet_system.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.wallet.Wallet;
import org.side_project.wallet_system.wallet.WalletRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member register(String name, int age, String email, String password) {
        log.info("Registering new member: email={}", email);
        if (memberRepository.existsByEmail(email)) {
            log.warn("Registration failed - email already exists: {}", email);
            throw new IllegalArgumentException("error.email.duplicate");
        }
        Member member = new Member();
        member.setName(name);
        member.setAge(age);
        member.setEmail(email);
        member.setPassword(passwordEncoder.encode(password));
        member.setAuthProvider(AuthProvider.LOCAL);
        member = memberRepository.save(member);

        createWalletFor(member);
        log.info("Member registered successfully: id={}, email={}", member.getId(), email);
        return member;
    }

    public Optional<Member> login(String email, String password) {
        log.debug("Login attempt: email={}", email);
        return memberRepository.findByEmail(email)
                .filter(m -> m.getPassword() != null
                        && passwordEncoder.matches(password, m.getPassword()));
    }

    @Transactional
    public Member findOrCreateGoogleMember(String googleId, String email, String name) {
        return memberRepository.findByGoogleId(googleId).map(member -> {
            log.info("Google OAuth2 login - existing member: id={}, email={}", member.getId(), email);
            return member;
        }).orElseGet(() -> {
            log.info("Google OAuth2 login - creating new member: email={}", email);
            if (memberRepository.existsByEmail(email)) {
                log.warn("Google OAuth2 registration failed - email already exists: {}", email);
                throw new IllegalArgumentException("error.email.duplicate");
            }
            Member member = new Member();
            member.setGoogleId(googleId);
            member.setEmail(email);
            member.setName(name);
            member.setAuthProvider(AuthProvider.GOOGLE);
            member = memberRepository.save(member);

            createWalletFor(member);
            log.info("Google member created: id={}, email={}", member.getId(), email);
            return member;
        });
    }

    private void createWalletFor(Member member) {
        Wallet wallet = new Wallet();
        wallet.setMember(member);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setWalletCode(Wallet.generateCode());
        walletRepository.save(wallet);
        log.debug("Wallet created for member: memberId={}, walletCode={}", member.getId(), wallet.getWalletCode());
    }
}
