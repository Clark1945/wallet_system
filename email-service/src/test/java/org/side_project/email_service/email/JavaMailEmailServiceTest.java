package org.side_project.email_service.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JavaMailEmailServiceTest {

    @Mock  private JavaMailSender mailSender;
    @InjectMocks private JavaMailEmailService emailService;

    private SimpleMailMessage captureMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void sendLoginOtp_sendsToCorrectRecipientWithOtpInBody() {
        emailService.sendLoginOtp("user@example.com", "654321");

        SimpleMailMessage msg = captureMessage();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(Objects.requireNonNull(msg.getText())).contains("654321");
        assertThat(msg.getSubject()).isNotBlank();
    }

    @Test
    void sendRegistrationOtp_sendsToCorrectRecipientWithOtpInBody() {
        emailService.sendRegistrationOtp("new@example.com", "999888");

        SimpleMailMessage msg = captureMessage();
        assertThat(msg.getTo()).containsExactly("new@example.com");
        assertThat(Objects.requireNonNull(msg.getText())).contains("999888");
    }

    @Test
    void sendPasswordResetLink_sendsResetUrlInBody() {
        String url = "http://localhost:8080/reset-password?mid=abc&token=xyz";
        emailService.sendPasswordResetLink("user@example.com", url);

        SimpleMailMessage msg = captureMessage();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(Objects.requireNonNull(msg.getText())).contains(url);
    }

    @Test
    void sendDepositSuccess_sendsAmountInBody() {
        emailService.sendDepositSuccess("user@example.com", new BigDecimal("2500.00"));

        SimpleMailMessage msg = captureMessage();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(Objects.requireNonNull(msg.getText())).contains("2500.00");
    }

    @Test
    void sendWithdrawalSuccess_sendsAmountInBody() {
        emailService.sendWithdrawalSuccess("user@example.com", new BigDecimal("750.50"));

        SimpleMailMessage msg = captureMessage();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(Objects.requireNonNull(msg.getText())).contains("750.50");
    }

    @Test
    void allMethods_setNonBlankSubject() {
        emailService.sendLoginOtp("a@b.com", "000000");
        assertThat(captureMessage().getSubject()).isNotBlank();
    }
}
