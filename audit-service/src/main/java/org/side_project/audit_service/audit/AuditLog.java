package org.side_project.audit_service.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit record stored in MongoDB. One document per security- or money-relevant
 * action, received from wallet_system via RabbitMQ. Never updated or deleted by application code.
 */
@Document(collection = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    private String id;

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

    /** Correlates the audit document with application logs via the MDC trace id. */
    private String traceId;

    private LocalDateTime createdAt;
}
