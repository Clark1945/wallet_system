package org.side_project.wallet_system.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit record. One row per security- or money-relevant action.
 * Rows are never updated or deleted by application code.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Member who performed the action; null for system/anonymous (e.g. failed login, webhook). */
    @Column(name = "actor_id")
    private UUID actorId;

    /** Actor email — kept for failed logins where the member id is unknown. */
    @Column(name = "actor_email")
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditResult result;

    /** What the action acted on, e.g. {@code TRANSACTION}, {@code WALLET}, {@code MEMBER}. */
    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    /** Monetary amount for financial actions; null otherwise. */
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    /** Free-form context or failure reason. */
    @Column(length = 500)
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** Correlates the audit row with application logs via the MDC trace id. */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}