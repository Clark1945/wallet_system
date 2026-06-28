package org.side_project.wallet_system.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit event built by {@link AuditService} and published to RabbitMQ for audit-service to
 * persist (in MongoDB). wallet_system no longer stores audit records itself, so this is a plain
 * event payload — not a JPA entity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    private UUID id;

    /** Member who performed the action; null for system/anonymous events (failed login, webhook). */
    private UUID actorId;

    /** Actor email — kept for failed logins where the member id is unknown. */
    private String actorEmail;

    private AuditAction action;

    private AuditResult result;

    /** What the action acted on, e.g. {@code TRANSACTION}, {@code WALLET}, {@code MEMBER}. */
    private String targetType;

    private String targetId;

    /** Monetary amount for financial actions; null otherwise. */
    private BigDecimal amount;

    /** Free-form context or failure reason. */
    private String detail;

    private String ipAddress;

    private String userAgent;

    /** Correlates the audit event with application logs via the MDC trace id. */
    private String traceId;

    private Instant createdAt;
}
