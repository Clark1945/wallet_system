package org.side_project.email_service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailListener {

    private final JavaMailEmailService emailService;

    @RabbitListener(queues = "${rabbitmq.queue}")
    public void handle(EmailMessage message) {
        log.info("Email message received: type={}, to={}", message.type(), message.to());
        try {
            switch (message.type()) {
                case EmailMessage.REGISTRATION_OTP ->
                        emailService.sendRegistrationOtp(message.to(), message.params().get("otp"));
                case EmailMessage.LOGIN_OTP ->
                        emailService.sendLoginOtp(message.to(), message.params().get("otp"));
                case EmailMessage.PASSWORD_RESET ->
                        emailService.sendPasswordResetLink(message.to(), message.params().get("resetUrl"));
                case EmailMessage.DEPOSIT_SUCCESS ->
                        emailService.sendDepositSuccess(message.to(),
                                new BigDecimal(message.params().get("amount")));
                case EmailMessage.WITHDRAWAL_SUCCESS ->
                        emailService.sendWithdrawalSuccess(message.to(),
                                new BigDecimal(message.params().get("amount")));
                default ->
                        log.warn("Unknown email type: {}", message.type());
            }
        } catch (Exception e) {
            log.error("Failed to send email: type={}, to={}, error={}", message.type(), message.to(), e.getMessage(), e);
            throw e;
        }
    }
}
