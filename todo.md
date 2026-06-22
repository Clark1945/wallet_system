# TODO — Audit domain extraction

Tracks the remaining work after moving audit logging out of `wallet_system` into a dedicated
event-driven **audit-service** (RabbitMQ → MongoDB).

_Last updated: 2026-06-21_

---

## ✅ Done (already on `master`)

- **wallet_system publishes audit events** instead of writing them inline — `AuditService.record()`
  enriches (IP / user-agent / trace id) then publishes via `AuditLogPublisher`
  (interface + `RabbitMQAuditLogPublisher` / `NoOpAuditLogPublisher`). Call sites unchanged.
- **audit-service** — new microservice: `@RabbitListener` on `audit.log.notification` →
  `AuditLogRepository.save()` into **MongoDB** (`AuditLog` is a Mongo `@Document`). Consistent
  `org.side_project.audit_service` package; email-service leftovers removed.
- **Spring Boot 4 fixes** — Jackson 2 `ObjectMapper` for `Jackson2JsonMessageConverter` (Boot 4
  auto-configures Jackson 3); MongoDB props under `spring.mongodb.*`; `INFERRED` type mapper so
  the publisher's `__TypeId__` header doesn't force loading wallet's class.
- **docker-compose** — added `mongo` + `audit-service` services and volumes.
- Publisher unit tests; coverage-gate script hardened (UTF-8 on Windows + honors
  `sonar.coverage.exclusions`).

## ✅ Done (PR #14, pending merge)

- **Removed wallet_system's dead audit persistence** — deleted `AuditLogRepository`; `AuditLog`
  is now a plain event payload (no JPA `@Entity`); `V4__drop_audit_logs.sql` drops the table.

---

## 🔲 Remaining

### 1. End-to-end verification (publish → Mongo)  ·  ✅ DONE
Verified locally with docker compose: published an audit event to `wallet.audit.log`; the
audit-service `@RabbitListener` consumed it and persisted a document to the MongoDB `audit_logs`
collection (`_id` = the stable event id; fields + `createdAt` intact). Redelivering the same event
three times left a single document — idempotency confirmed.

### 2. Stamp `createdAt` at the publisher  ·  ✅ DONE
`AuditService.record()` now stamps `createdAt = now()` (event time) before publishing, since the
`@PrePersist` hook is gone. The consumer keeps a null-fallback as defence.

### 3. Idempotent consumer  ·  ✅ DONE
`AuditService.record()` now stamps a stable `id` (UUID) on each event; the consumer uses it as the
Mongo `_id`, so `save()` upserts and an at-least-once redelivery overwrites the same document
instead of duplicating it.

### 4. CI job for audit-service  ·  ✅ DONE
`test-audit-service` job added to `.github/workflows/ci.yml` (mirrors `test-email-service`).

### 5. Observability wiring  ·  ✅ DONE
Prometheus scrapes `audit-service:8084/actuator/prometheus`; Promtail ships its logs to Loki
(mounted `audit_service_logs` + scrape job); added to prometheus/promtail `depends_on`.

### 6. Minor / cleanup  ·  ✅ DONE
- logback now writes `audit-service.log` and DEBUGs `org.side_project.audit_service`.
- Dockerfile `EXPOSE` is 8084.
- **DLQ ownership**: kept dual declaration on purpose — the queue stays present regardless of
  startup order, so no audit events are lost.
