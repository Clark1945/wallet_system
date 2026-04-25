package org.side_project.wallet_system.auth.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class JavaMailEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Async
    @Override
    public void sendLoginOtp(String to, String otp) {
        send(to, "Your login verification code",
                "Your login verification code is: " + otp +
                "\n\nThis code expires in 10 minutes." +
                "\nIf you did not attempt to log in, please ignore this email.");
    }

    @Async
    @Override
    public void sendRegistrationOtp(String to, String otp) {
        send(to, "Verify your email to complete registration",
                "Your email verification code is: " + otp +
                "\n\nThis code expires in 10 minutes." +
                "\nEnter this code to complete your registration.");
    }

    @Async
    @Override
    public void sendPasswordResetLink(String to, String resetUrl) {
        send(to, "Reset your password",
                "Click the link below to reset your password:\n" + resetUrl +
                "\n\nThis link expires in 15 minutes." +
                "\nIf you did not request a password reset, please ignore this email.");
    }

    @Async
    @Override
    public void sendDepositSuccess(String to, BigDecimal amount) {
        send(to, "Deposit successful",
                "Your deposit of " + amount.toPlainString() + " has been completed successfully." +
                "\n\nThank you for using Wallet System.");
    }

    @Async
    @Override
    public void sendWithdrawalSuccess(String to, BigDecimal amount) {
        send(to, "Withdrawal successful",
                "Your withdrawal of " + amount.toPlainString() + " has been completed successfully." +
                "\n\nThank you for using Wallet System.");
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("Email sent: to={}, subject={}", to, subject);
    }
}
