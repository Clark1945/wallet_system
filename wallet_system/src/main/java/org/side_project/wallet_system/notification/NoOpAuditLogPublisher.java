package org.side_project.wallet_system.notification;

import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.audit.AuditLog;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test-profile no-op so AuditService (always active) can be wired without RabbitMQ.
 * Mirrors {@link NoOpEmailPublisher}.
 */
@Slf4j
@Component
@Profile("test")
public class NoOpAuditLogPublisher implements AuditLogPublisher {

    @Override
    public void publish(AuditLog message) {
        log.info("[TEST] audit log published: action={}, actorId={}", message.getAction(), message.getActorId());
    }
}
