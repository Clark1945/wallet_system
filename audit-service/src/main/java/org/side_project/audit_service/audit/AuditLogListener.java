package org.side_project.audit_service.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Consumes audit events published by wallet_system (exchange {@code wallet.audit.log}) and
 * persists them to MongoDB.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class AuditLogListener {

    private final AuditLogRepository auditLogRepository;

    @RabbitListener(queues = "${rabbitmq.queue}")
    public void handle(AuditLog auditLog) {
        // The publisher no longer persists, so createdAt may be unset — stamp it on receipt.
        if (auditLog.getCreatedAt() == null) {
            auditLog.setCreatedAt(LocalDateTime.now());
        }
        auditLogRepository.save(auditLog);
        log.info("Audit log persisted: action={}, actorId={}, traceId={}",
                auditLog.getAction(), auditLog.getActorId(), auditLog.getTraceId());
    }
}
