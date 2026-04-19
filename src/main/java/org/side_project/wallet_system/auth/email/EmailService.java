package org.side_project.wallet_system.auth.email;

public interface EmailService {
    void sendLoginOtp(String to, String otp);
    void sendRegistrationOtp(String to, String otp);
    void sendPasswordResetLink(String to, String resetUrl);
}
