package org.side_project.wallet_system.auth;

import lombok.RequiredArgsConstructor;
import org.side_project.wallet_system.wallet.Wallet;
import org.side_project.wallet_system.wallet.WalletRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member register(String name, int age, String email, String password) {
        if (memberRepository.existsByEmail(email)) {
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
        return member;
    }

    public Optional<Member> login(String email, String password) {
        return memberRepository.findByEmail(email)
                .filter(m -> m.getPassword() != null
                        && passwordEncoder.matches(password, m.getPassword()));
    }

    @Transactional
    public Member findOrCreateGoogleMember(String googleId, String email, String name) {
        return memberRepository.findByGoogleId(googleId).orElseGet(() -> {
            if (memberRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("error.email.duplicate");
            }
            Member member = new Member();
            member.setGoogleId(googleId);
            member.setEmail(email);
            member.setName(name);
            member.setAuthProvider(AuthProvider.GOOGLE);
            member = memberRepository.save(member);

            createWalletFor(member);
            return member;
        });
    }

    private void createWalletFor(Member member) {
        Wallet wallet = new Wallet();
        wallet.setMember(member);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setWalletCode(Wallet.generateCode());
        walletRepository.save(wallet);
    }
}
