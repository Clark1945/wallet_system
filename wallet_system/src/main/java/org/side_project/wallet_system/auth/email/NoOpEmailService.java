package org.side_project.wallet_system.auth.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@Profile("test")
public class NoOpEmailService implements EmailService {

    @Override
    public void sendLoginOtp(String to, String otp) {
        log.info("[TEST] sendLoginOtp: to={}, otp={}", to, otp);
    }

    @Override
    public void sendRegistrationOtp(String to, String otp) {
        log.info("[TEST] sendRegistrationOtp: to={}, otp={}", to, otp);
    }

    @Override
    public void sendPasswordResetLink(String to, String resetUrl) {
        log.info("[TEST] sendPasswordResetLink: to={}, url={}", to, resetUrl);
    }

    @Override
    public void sendDepositSuccess(String to, BigDecimal amount) {
        log.info("[TEST] sendDepositSuccess: to={}, amount={}", to, amount);
    }

    @Override
    public void sendWithdrawalSuccess(String to, BigDecimal amount) {
        log.info("[TEST] sendWithdrawalSuccess: to={}, amount={}", to, amount);
    }
}
