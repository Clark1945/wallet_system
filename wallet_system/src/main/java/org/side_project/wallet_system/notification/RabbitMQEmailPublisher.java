package org.side_project.wallet_system.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RabbitMQEmailPublisher implements EmailPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Override
    public void sendRegistrationOtp(String to, String otp) {
        publish(new EmailMessage(EmailMessage.REGISTRATION_OTP, to, Map.of("otp", otp)));
    }

    @Override
    public void sendLoginOtp(String to, String otp) {
        publish(new EmailMessage(EmailMessage.LOGIN_OTP, to, Map.of("otp", otp)));
    }

    @Override
    public void sendPasswordResetLink(String to, String resetUrl) {
        publish(new EmailMessage(EmailMessage.PASSWORD_RESET, to, Map.of("resetUrl", resetUrl)));
    }

    @Override
    public void sendDepositSuccess(String to, BigDecimal amount) {
        publish(new EmailMessage(EmailMessage.DEPOSIT_SUCCESS, to, Map.of("amount", amount.toPlainString())));
    }

    @Override
    public void sendWithdrawalSuccess(String to, BigDecimal amount) {
        publish(new EmailMessage(EmailMessage.WITHDRAWAL_SUCCESS, to, Map.of("amount", amount.toPlainString())));
    }

    private void publish(EmailMessage message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.debug("Email message published: type={}, to={}", message.type(), message.to());
    }
}
