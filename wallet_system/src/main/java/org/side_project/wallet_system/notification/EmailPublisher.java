package org.side_project.wallet_system.notification;

import java.math.BigDecimal;

public interface EmailPublisher {
    void sendRegistrationOtp(String to, String otp);
    void sendLoginOtp(String to, String otp);
    void sendPasswordResetLink(String to, String resetUrl);
    void sendDepositSuccess(String to, BigDecimal amount);
    void sendWithdrawalSuccess(String to, BigDecimal amount);
}
