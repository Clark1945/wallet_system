package org.side_project.wallet_system.notification;

import org.side_project.wallet_system.audit.AuditLog;

/**
 * Publishes audit events to the audit domain (currently RabbitMQ).
 * Implementations are best-effort and must never throw to the caller.
 */
public interface AuditLogPublisher {

    void publish(AuditLog message);
}
