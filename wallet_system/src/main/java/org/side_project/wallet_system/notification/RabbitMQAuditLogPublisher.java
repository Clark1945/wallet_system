package org.side_project.wallet_system.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.audit.AuditLog;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RabbitMQAuditLogPublisher implements AuditLogPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.auditLog.exchange}")
    private String exchange;

    @Value("${rabbitmq.auditLog.routing-key}")
    private String routingKey;

    @Override
    public void publish(AuditLog message) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.debug("Audit log published: action={}, actorId={}", message.getAction(), message.getActorId());
        } catch (Exception e) {
            log.error("Failed to publish audit log: action={}, actorId={}", message.getAction(), message.getActorId(), e);
        }
    }
}
