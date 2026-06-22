package org.side_project.wallet_system.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.side_project.wallet_system.notification.AuditLogPublisher;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records audit events. Enriches each entry with request context (IP, user agent, trace id) and
 * publishes it via {@link AuditLogPublisher} for the audit domain to consume and persist.
 *
 * <p><b>Never breaks the caller</b> — any failure to record is logged and swallowed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int USER_AGENT_MAX = 255;

    private final AuditLogPublisher auditLogPublisher;

    /**
     * Records an audit entry, enriching it with the current request's IP, user agent and trace id
     * when available, then publishing it as an event. Best-effort: never throws.
     */
    public void record(AuditLog entry) {
        if (entry == null) {
            return;
        }
        try {
            // wallet no longer persists, so the old @PrePersist hook is gone — stamp here instead.
            // The id is a stable event id: the consumer uses it as the Mongo _id so save() upserts,
            // making at-least-once redeliveries idempotent. createdAt is the event time.
            if (entry.getId() == null) {
                entry.setId(UUID.randomUUID());
            }
            if (entry.getCreatedAt() == null) {
                entry.setCreatedAt(LocalDateTime.now());
            }
            enrichFromRequestContext(entry);
            auditLogPublisher.publish(entry);
        } catch (Exception e) {
            log.error("Failed to record audit log: action={}, actorId={}", entry.getAction(), entry.getActorId(), e);
        }
    }

    private void enrichFromRequestContext(AuditLog entry) {
        if (entry.getTraceId() == null) {
            entry.setTraceId(MDC.get("traceId"));
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            // No bound request (async withdrawal callback worker, scheduled job) — context fields stay null.
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        if (entry.getIpAddress() == null) {
            entry.setIpAddress(clientIp(request));
        }
        if (entry.getUserAgent() == null) {
            entry.setUserAgent(truncate(request.getHeader("User-Agent")));
        }
    }

    /** Honors the first hop in X-Forwarded-For when behind the reverse proxy, else the socket address. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > USER_AGENT_MAX ? value.substring(0, USER_AGENT_MAX) : value;
    }
}