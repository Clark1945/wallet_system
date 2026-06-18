package org.side_project.wallet_system.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes audit records. Two guarantees:
 * <ul>
 *   <li><b>Survives business rollback</b> — {@link #persist} runs in a new transaction
 *       ({@code REQUIRES_NEW}), so a failed/refunded operation is still recorded.</li>
 *   <li><b>Never breaks the caller</b> — any failure to audit is logged and swallowed.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int USER_AGENT_MAX = 255;

    private final AuditLogRepository auditLogRepository;

    // Self-injection (lazy) so persist() runs through the Spring proxy (REQUIRES_NEW).
    @Lazy
    @Autowired
    private AuditService self;

    /**
     * Records an audit entry, enriching it with the current request's IP, user agent and trace id
     * when available. Best-effort: never throws.
     */
    public void record(AuditLog entry) {
        if (entry == null) {
            return;
        }
        try {
            enrichFromRequestContext(entry);
            self.persist(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log: action={}, actorId={}", entry.getAction(), entry.getActorId(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AuditLog entry) {
        auditLogRepository.save(entry);
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