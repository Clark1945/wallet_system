package org.side_project.email_service.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailListenerTest {

    @Mock  private JavaMailEmailService emailService;
    @InjectMocks private EmailListener listener;

    @Test
    void handle_registrationOtp_callsSendRegistrationOtp() {
        listener.handle(new EmailMessage(
                EmailMessage.REGISTRATION_OTP, "alice@example.com", Map.of("otp", "123456")));

        then(emailService).should().sendRegistrationOtp("alice@example.com", "123456");
    }

    @Test
    void handle_loginOtp_callsSendLoginOtp() {
        listener.handle(new EmailMessage(
                EmailMessage.LOGIN_OTP, "alice@example.com", Map.of("otp", "654321")));

        then(emailService).should().sendLoginOtp("alice@example.com", "654321");
    }

    @Test
    void handle_passwordReset_callsSendPasswordResetLink() {
        String url = "http://localhost:8080/reset-password?mid=abc&token=xyz";
        listener.handle(new EmailMessage(
                EmailMessage.PASSWORD_RESET, "alice@example.com", Map.of("resetUrl", url)));

        then(emailService).should().sendPasswordResetLink("alice@example.com", url);
    }

    @Test
    void handle_depositSuccess_callsSendDepositSuccess() {
        listener.handle(new EmailMessage(
                EmailMessage.DEPOSIT_SUCCESS, "alice@example.com", Map.of("amount", "1000.00")));

        then(emailService).should().sendDepositSuccess("alice@example.com", new BigDecimal("1000.00"));
    }

    @Test
    void handle_withdrawalSuccess_callsSendWithdrawalSuccess() {
        listener.handle(new EmailMessage(
                EmailMessage.WITHDRAWAL_SUCCESS, "alice@example.com", Map.of("amount", "500.50")));

        then(emailService).should().sendWithdrawalSuccess("alice@example.com", new BigDecimal("500.50"));
    }

    @Test
    void handle_unknownType_doesNotCallEmailService() {
        listener.handle(new EmailMessage("UNKNOWN_TYPE", "alice@example.com", Map.of()));

        verifyNoInteractions(emailService);
    }

    @Test
    void handle_emailServiceThrows_exceptionPropagates() {
        willThrow(new RuntimeException("SMTP connection refused"))
                .given(emailService).sendLoginOtp("alice@example.com", "111111");

        assertThatThrownBy(() -> listener.handle(new EmailMessage(
                EmailMessage.LOGIN_OTP, "alice@example.com", Map.of("otp", "111111"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("SMTP connection refused");
    }
}
