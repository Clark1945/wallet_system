package org.side_project.wallet_system.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.notification.EmailPublisher;
import org.side_project.wallet_system.auth.objects.AuthProvider;
import org.side_project.wallet_system.auth.objects.Member;
import org.side_project.wallet_system.auth.objects.MemberStatus;
import org.side_project.wallet_system.auth.objects.OtpType;
import org.side_project.wallet_system.auth.repository.MemberRepository;
import org.side_project.wallet_system.wallet.Wallet;
import org.side_project.wallet_system.wallet.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final EmailPublisher emailPublisher;
    private final PasswordResetService passwordResetService;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Transactional
    public Member initiateRegistration(String name, int age, String email, String password) {
        log.info("Registration initiated: email={}", email);
        memberRepository.findByEmail(email).ifPresent(existing -> {
            if (existing.getStatus() == MemberStatus.ACTIVE) {
                log.warn("Registration failed - email already active: {}", email);
                throw new IllegalArgumentException("error.email.duplicate");
            }
            // Stale PENDING member: remove and recreate with fresh OTP
            memberRepository.delete(existing);
            memberRepository.flush();
        });

        Member member = new Member();
        member.setName(name);
        member.setAge(age);
        member.setEmail(email);
        member.setPassword(passwordEncoder.encode(password));
        member.setAuthProvider(AuthProvider.LOCAL);
        member.setStatus(MemberStatus.PENDING);
        member = memberRepository.save(member);
        log.info("PENDING member created: id={}, email={}", member.getId(), email);
        return member;
    }

    public static final String TEST_EMAIL = "test1234@gmail.com";
    private static final String TEST_FIXED_OTP = "123456";

    @Transactional
    public void ensureTestMemberActive() {
        Optional<Member> existing = memberRepository.findByEmail(TEST_EMAIL);
        if (existing.isPresent() && existing.get().getStatus() == MemberStatus.ACTIVE) {
            return;
        }
        existing.ifPresent(m -> {
            walletRepository.findByMemberId(m.getId()).ifPresent(walletRepository::delete);
            walletRepository.flush();
            memberRepository.delete(m);
            memberRepository.flush();
        });
        Member member = new Member();
        member.setName("Test User");
        member.setEmail(TEST_EMAIL);
        member.setPassword(passwordEncoder.encode("test1234"));
        member.setAuthProvider(AuthProvider.LOCAL);
        member.setStatus(MemberStatus.ACTIVE);
        member = memberRepository.save(member);
        createWalletFor(member);
        log.info("Test member seeded: id={}", member.getId());
    }

    public void sendRegistrationOtp(UUID memberId, String email) {
        String otp = otpService.generateAndStore(memberId, OtpType.REGISTER);
        emailPublisher.sendRegistrationOtp(email, otp);
    }

    @Transactional
    public void verifyAndActivate(UUID memberId, String code) {
        if (!otpService.verify(memberId, OtpType.REGISTER, code)) {
            throw new IllegalArgumentException("error.otp.invalid");
        }
        activateRegistration(memberId);
    }

    public void sendLoginOtp(UUID memberId, String email) {
        if (TEST_EMAIL.equals(email)) {
            otpService.storeFixedCode(memberId, OtpType.LOGIN, TEST_FIXED_OTP);
        } else {
            String otp = otpService.generateAndStore(memberId, OtpType.LOGIN);
            emailPublisher.sendLoginOtp(email, otp);
        }
    }

    public void verifyLoginOtpCode(UUID memberId, String code) {
        if (!otpService.verify(memberId, OtpType.LOGIN, code)) {
            throw new IllegalArgumentException("error.otp.invalid");
        }
    }

    public void initiatePasswordReset(String email) {
        findByEmail(email).ifPresent(member -> {
            if (member.getStatus() == MemberStatus.ACTIVE
                    && member.getAuthProvider() == AuthProvider.LOCAL) {
                String token    = passwordResetService.generateToken(member.getId());
                String resetUrl = appBaseUrl + "/reset-password?mid=" + member.getId() + "&token=" + token;
                emailPublisher.sendPasswordResetLink(email, resetUrl);
                log.info("Password reset link sent: memberId={}", member.getId());
            }
        });
    }

    @Transactional
    public void activateRegistration(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("error.member.not.found"));
        if (member.getStatus() == MemberStatus.ACTIVE) {
            return; // idempotent
        }
        member.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(member);
        createWalletFor(member);
        log.info("Member activated: id={}, email={}", member.getId(), member.getEmail());
    }

    @Transactional
    public void updateLastLogin(UUID memberId) {
        memberRepository.findById(memberId).ifPresent(m -> {
            m.setLastLoginAt(LocalDateTime.now());
            memberRepository.save(m);
        });
    }

    @Transactional
    public void resetPassword(UUID memberId, String newPassword) {
        memberRepository.findById(memberId).ifPresent(m -> {
            m.setPassword(passwordEncoder.encode(newPassword));
            memberRepository.save(m);
            log.info("Password reset: memberId={}", memberId);
        });
    }

    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    public String getEmailById(UUID memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getEmail)
                .orElseThrow(() -> new IllegalArgumentException("error.member.not.found"));
    }

    public String getNameById(UUID memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getName)
                .orElseThrow(() -> new IllegalArgumentException("error.member.not.found"));
    }

    public Optional<Member> login(String email, String password) {
        log.debug("Login attempt: email={}", email);
        return memberRepository.findByEmail(email)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE
                        && m.getPassword() != null
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
            member.setStatus(MemberStatus.ACTIVE);
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
        log.debug("Wallet created: memberId={}, walletCode={}", member.getId(), wallet.getWalletCode());
    }
}
