# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Monorepo Structure

Five Spring Boot services, fronted by an nginx reverse proxy:
- **`wallet_system/`** — main wallet application (port 8080); detailed guidance in `wallet_system/CLAUDE.md`
- **`mock-bank/`** — simulated bank withdrawal endpoint (port 8081)
- **`payment-service/`** — Stripe + SBPS payment gateway handler (port 8082)
- **`email-service/`** — async email dispatcher (port 8083); consumes RabbitMQ messages and sends via SMTP
- **`audit-service/`** — async audit-log consumer (port 8084); consumes RabbitMQ messages and persists them to MongoDB
- **`nginx/`** — reverse proxy and single browser-facing HTTP entry point (port 80); routes `/payment/**` to payment-service and everything else to the wallet app. See `nginx/nginx.conf`.

## Git Hooks (pre-push gate)

Version-controlled hooks live in `.githooks/`. Enable them **once per clone**:

```bash
git config core.hooksPath .githooks
```

Two hooks, both auto-skipping when no `wallet_system/src` changes are involved, and both
using the SDKMAN-default JDK (keep it on Java 17 — JaCoCo 0.8.12 cannot instrument Java 25):

- **`.githooks/pre-commit`** — runs the wallet_system test suite before a commit is created,
  so you never commit on top of failing tests. Bypass with `git commit --no-verify`.
- **`.githooks/pre-push`** — runs the test suite + the new-code coverage check (the same gate
  as CI / the `coverage-gate` skill) before a push. Coverage lives here, not in pre-commit,
  because it diffs committed history (`base...HEAD`). Bypass with `git push --no-verify`.

## Local development & Spring Boot 4 notes

- **Use Java 17 locally.** JaCoCo 0.8.12 cannot instrument Java 25, so `./mvnw test` (with
  coverage) fails on a Java 25 JDK. Use SDKMAN: `sdk use java 17`. The git hooks source the
  SDKMAN default, so keep it on 17.
- **Spring Boot 4 uses Jackson 3** (`tools.jackson`) for the auto-configured `ObjectMapper`, but
  Spring AMQP's `Jackson2JsonMessageConverter` needs a **Jackson 2** (`com.fasterxml.jackson`)
  mapper — construct your own `new ObjectMapper()` and add `jackson-datatype-jsr310` +
  `JavaTimeModule` for `java.time` fields (see each service's `RabbitMQConfig`).
- **Cross-service deserialization:** publishers stamp a `__TypeId__` header; consumers set
  `DefaultJackson2JavaTypeMapper` to `TypePrecedence.INFERRED` so messages deserialize into the
  consumer's own type, not the publisher's class.
- **MongoDB props live under `spring.mongodb.*`** in Boot 4 (moved from `spring.data.mongodb.*`).
- **Test-slice annotations moved to module packages**, e.g. `@DataJpaTest` →
  `org.springframework.boot.data.jpa.test.autoconfigure`, `@WebMvcTest` →
  `org.springframework.boot.webmvc.test.autoconfigure`.

## Build & Run Commands

```bash
# Full stack — run from repo root
# Requires wallet_system/.env (copy from .env.example and fill in credentials)
# Starts: nginx, PostgreSQL, Redis, MongoDB, RabbitMQ, mock-bank, wallet app, payment-service,
#         email-service, audit-service, Prometheus, postgres-exporter, redis-exporter,
#         Loki, Promtail, Grafana
docker compose up --build
# Entry point (via nginx): http://localhost  |  Grafana: http://localhost:3000
# The wallet app's own port 8080 is also published for direct access during development.

# email-service only
cd email-service
./mvnw spring-boot:run

# audit-service only
cd audit-service
./mvnw spring-boot:run

# wallet_system only
cd wallet_system
./mvnw spring-boot:run      # run
./mvnw clean package        # build JAR
./mvnw test                 # all tests
./mvnw test -Dtest=AuthServiceTest          # single class
./mvnw test -Dtest=AuthServiceTest#registerSuccess  # single method

# payment-service only
cd payment-service
./mvnw spring-boot:run

# mock-bank only
cd mock-bank
./mvnw spring-boot:run
```

**Rule: run `./mvnw test` after every code change inside `wallet_system/` and fix all failures before finishing.**

## Email Notification Flow (Cross-Service via RabbitMQ)

All email sending is fully decoupled from business logic through RabbitMQ:

1. `wallet_system` publishes an `EmailMessage { type, to, params }` JSON message via `EmailPublisher` → `RabbitTemplate.convertAndSend(exchange="wallet.email", routingKey="email.notification")`
2. RabbitMQ routes to durable queue `email.notifications`
3. `email-service` `@RabbitListener` consumes the message and calls `JavaMailEmailService` to send via SMTP

Email types: `REGISTRATION_OTP`, `LOGIN_OTP`, `PASSWORD_RESET`, `DEPOSIT_SUCCESS`, `WITHDRAWAL_SUCCESS`

Test profile: `NoOpEmailPublisher` (`@Profile("test")`) and `RabbitAutoConfiguration` excluded — no RabbitMQ needed for tests.
RabbitMQ Management UI: `http://localhost:15672` (credentials from `RABBITMQ_USER`/`RABBITMQ_PASS`)

## Audit Log Flow (Cross-Service via RabbitMQ)

Audit logging is decoupled from business logic the same way email is:

1. `wallet_system` enriches an `AuditLog` (actor, action, result, request context) and **publishes** it via `AuditLogPublisher` → `RabbitTemplate.convertAndSend(exchange="wallet.audit.log", routingKey="audit.log.notification")`. It no longer writes to its own DB.
2. RabbitMQ routes to durable queue `audit.log.notification` (with dead-letter queue `audit.log.notification.dlq` for poison messages).
3. `audit-service` `@RabbitListener` consumes the message and persists it to the MongoDB `audit_logs` collection.

The publisher stamps a `__TypeId__` header; `audit-service` configures `Jackson2JavaTypeMapper.TypePrecedence.INFERRED` so the message deserializes into its own `AuditLog` type. The Jackson 2 `ObjectMapper` is built explicitly (Spring Boot 4 auto-configures a Jackson 3 bean) with `JavaTimeModule` registered for the `java.time` fields.

Test profile: `NoOpAuditLogPublisher` (`@Profile("test")`) on the wallet side — no RabbitMQ/MongoDB needed for tests.

## Deposit Flow (Cross-Service)

Deposits span wallet_system and payment-service:

1. User POSTs to `/deposit` → `WalletController` calls `PaymentTokenService.createToken()` which stores `{memberId, amount, method}` in Redis (15-minute TTL) under key `payment_token:{uuid}` and returns the token UUID.
2. User is redirected to `payment-service:8082/payment/{stripe|sbpayment}/checkout?token={token}`.
3. payment-service calls `GET wallet-service:8080/internal/token/{token}` to validate and consume the token (one-time use).
4. payment-service initiates the deposit via `POST /internal/deposit/initiate`, receives a `transactionId`, then interacts with the payment gateway (Stripe or SBPS).
5. On payment success (Stripe webhook or SBPS result CGI), payment-service calls `POST /internal/deposit/complete` or `/internal/deposit/complete-by-external`.
6. All `/internal/**` calls require the `X-Internal-Secret` header; wallet_system rejects with `401` if missing or wrong.

## Withdrawal Flow (Cross-Service)

1. User POSTs to `/withdraw` → `WalletService.initiateWithdrawal()` deducts balance, creates `Transaction` with status `REQUEST_COMPLETED`, then asynchronously (`CompletableFuture.runAsync`) calls `MockBankClient.sendWithdrawRequest()` to `POST mock-bank:8081/api/withdraw`.
2. mock-bank responds `200 OK` immediately, then fires a callback to `POST wallet_system:8080/withdraw/webhook` after a 3–8 second random delay.
3. `WithdrawWebhookController` verifies `X-Webhook-Signature: sha256=<hex>` (HMAC-SHA256 of raw body, key = `WITHDRAW_WEBHOOK_SECRET`), then sets `Transaction.status = COMPLETED` on SUCCESS or `FAILED` + refunds balance on FAIL.
4. **Circuit breaker:** `MockBankClient.sendWithdrawRequest()` is wrapped with resilience4j `@CircuitBreaker(name = "mockBankWithdraw")`; mock-bank 5xx responses count as failures. When the circuit is OPEN the fallback throws `MockBankUnavailableException`, and `WalletService` refunds the withdrawal immediately instead of waiting for the timeout job.
5. **Timeout safety:** `TransactionTimeoutJob` (`@Scheduled(fixedDelay = 60_000)`) runs every 60 seconds and refunds any `PENDING` or `REQUEST_COMPLETED` transaction older than 5 minutes.

Transaction status lifecycle: `PENDING` → `REQUEST_COMPLETED` → `COMPLETED` (or `FAILED` on timeout). Status transitions use an atomic CAS update (`TransactionRepository.compareAndSetStatus`) so concurrent/duplicate webhook or timeout calls can never double-credit or double-refund.

## mock-bank

Single endpoint: `POST /api/withdraw` accepts `{ transactionId, amount, bankCode, bankAccount, callbackUrl }`. Returns `200 OK` synchronously, then calls `callbackUrl` after random 3–8 s delay with an HMAC-SHA256-signed payload `{ transactionId, result: "SUCCESS"|"FAIL" }`. No database — all in-memory.

Configurable failure simulation (`mock-bank/src/main/resources/application.properties`):
- `mock-bank.fail-rate=0.10` — 10% of callbacks return `FAIL`
- `mock-bank.no-callback-rate=0.10` — 10% of requests send no callback (triggers timeout refund after 5 min)

## payment-service Architecture

**Base package:** `org.side_project.payment_service`

- `payment/` — `StripePaymentController` + `StripePaymentService`, `SBPaymentController` + `SBPaymentService` + `SBPaymentRequest`
- `client/` — `WalletServiceClient` (RestClient wrapper for all `/internal/**` calls to wallet_system); `dto/` contains shared record types
- `config/` — `SecurityConfig` (3 chains), `AppConfig`, `WebConfig`, `GlobalExceptionHandler`

**Authentication:** No user sessions. All payment flows are authenticated by a one-time payment token validated via wallet-service's internal API. The `X-Internal-Secret` header authenticates service-to-service calls back to wallet_system.

**Security filter chains** (`@Order`, lower = higher priority):
1. **SBPS callback** (`@Order(1)`) — STATELESS, CSRF disabled; matches `/payment/sbpayment/result`
2. **Stripe webhook** (`@Order(2)`) — STATELESS, CSRF disabled; matches `/payment/stripe/webhook`
3. **Main chain** (`@Order(3)`) — STATELESS, CSRF disabled; all other routes

**Required env vars for payment-service:**
```
STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY, STRIPE_WEBHOOK_SECRET
PAYMENT_MERCHANT_ID, PAYMENT_SERVICE_ID, PAYMENT_HASH_KEY
SP_PAYMENT_REDIRECT_URL         # browser-visible base URL for SBPS redirect
WALLET_SERVICE_BASE_URL         # container-to-container (e.g. http://app:8080)
WALLET_SERVICE_PUBLIC_URL       # browser redirect after payment (e.g. http://localhost:8080)
INTERNAL_SERVICE_SECRET         # must match wallet_system's value
```

## wallet_system Key Architecture

See `wallet_system/CLAUDE.md` for complete details.

### Authentication Flows

- **Form login:** email + password, `BCryptPasswordEncoder`
- **Google OAuth2/OIDC:** `CustomOAuth2UserService` + `LoginSuccessHandler`
- **Email OTP:** `OtpService` stores 6-digit code in Redis (10-min TTL); `OtpController` verifies and completes login
- **Password reset:** `PasswordResetService` stores UUID token in Redis (15-min TTL); token is one-time-use
- **Account lockout:** `LoginAttemptService` tracks failed attempts in Redis (`login_attempts:{email}`); locks after threshold

### Security Filter Chains (wallet_system)

`SecurityConfig` configures **3 filter chains** (`@Order`, lower = higher priority):
1. **Internal API chain** (`@Order(1)`) — STATELESS, CSRF disabled; validates `X-Internal-Secret` header; matches `/internal/**`
2. **Withdrawal webhook chain** (`@Order(2)`) — STATELESS, CSRF disabled, no Spring Security auth; matches `/withdraw/webhook` (HMAC-SHA256 verified in `WithdrawWebhookController`)
3. **Main chain** (default) — form login + OAuth2 + session management; all other app routes

### Transaction Filtering & Pagination

`TransactionSpec` builds JPA `Specification` predicates for type (`DEPOSIT`/`WITHDRAW`/`TRANSFER`) and date range. `TransactionRepository` extends `JpaSpecificationExecutor`. Controllers pass a `Pageable` (default 10 per page, ordered by `createdAt` DESC).

### Audit Log

The `audit/` package builds an append-only trail of auth and money events. `AuditService.record()` enriches each entry with request context and **publishes** it to RabbitMQ via `AuditLogPublisher` (it no longer writes to a DB itself); `audit-service` consumes and persists to MongoDB. Publishing failures are logged and swallowed so auditing never breaks the caller. See the "Audit Log Flow" section above and `wallet_system/CLAUDE.md` for the full field list and wiring.
