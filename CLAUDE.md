# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Monorepo Structure

Two Spring Boot services:
- **`wallet_system/`** — main wallet application (port 8080); detailed guidance in `wallet_system/CLAUDE.md`
- **`mock-bank/`** — simulated bank withdrawal endpoint (port 8081)

## Build & Run Commands

All commands run from within the respective service directory (`cd wallet_system` or `cd mock-bank`).

```bash
# Full stack (requires wallet_system/.env — copy from .env.example and fill in credentials)
# Starts: PostgreSQL, Redis, mock-bank, wallet app, Loki, Promtail, Grafana
docker compose up --build
# wallet app: http://localhost:8080  |  Grafana: http://localhost:3000

# wallet_system only
cd wallet_system
./mvnw spring-boot:run      # run
./mvnw clean package        # build JAR
./mvnw test                 # all tests
./mvnw test -Dtest=AuthServiceTest          # single class
./mvnw test -Dtest=AuthServiceTest#registerSuccess  # single method

# mock-bank only
cd mock-bank
./mvnw spring-boot:run
```

**Rule: run `./mvnw test` after every code change inside `wallet_system/` and fix all failures before finishing.**

## Cross-Service: Withdrawal Flow

Withdrawals span both services asynchronously:

1. User POSTs to `/withdraw` → `WalletService.withdraw()` deducts balance, creates `Transaction` with status `REQUEST_COMPLETED`, then calls `HttpClient.sendAsync()` to `POST mock-bank:8081/api/withdraw`
2. mock-bank responds `200 OK` immediately, then fires a callback to `POST wallet_system:8080/withdraw/webhook` after a 3–8 second random delay
3. `WithdrawWebhookController` verifies `X-Webhook-Signature: sha256=<hex>` (HMAC-SHA256 of raw body, key = `WITHDRAW_WEBHOOK_SECRET`), then sets `Transaction.status = COMPLETED` on SUCCESS or `FAILED` + refunds balance on FAIL
4. **Timeout safety:** `TransactionTimeoutJob` (`@Scheduled(fixedDelay = 60_000)`) runs every 60 seconds and refunds any `PENDING` or `REQUEST_COMPLETED` transaction older than 5 minutes (sets status `FAILED`, restores balance)

Transaction status lifecycle: `PENDING` → `REQUEST_COMPLETED` → `COMPLETED` (or `FAILED` on timeout)

The webhook endpoint (`/withdraw/webhook`) has its own Spring Security filter chain: STATELESS, no CSRF, no Spring Security auth. HMAC verification is enforced inside `WithdrawWebhookController`.

## mock-bank

Single endpoint: `POST /api/withdraw` accepts `{ transactionId, amount, bankCode, bankAccount, callbackUrl }`. Returns `200 OK` synchronously, then calls `callbackUrl` after random 3–8 s delay with an HMAC-SHA256-signed payload `{ transactionId, result: "SUCCESS"|"FAIL" }`. No database — all in-memory.

Configurable failure simulation (`mock-bank/src/main/resources/application.properties`):
- `mock-bank.fail-rate=0.10` — 10% of callbacks return `FAIL` (triggers refund)
- `mock-bank.no-callback-rate=0.10` — 10% of requests send no callback at all (triggers timeout refund after 5 min)

## wallet_system Key Architecture

See `wallet_system/CLAUDE.md` for complete details. Highlights not covered there:

### Authentication Flows

Three login methods beyond form login and Google OAuth2:
- **Email OTP:** `OtpService` generates a 6-digit code, stores it in Redis (10-minute TTL), sends via `EmailService`. `OtpController` verifies and completes login.
- **Password reset:** `PasswordResetService` generates a UUID token stored in Redis (15-minute TTL), emails a reset link. Token is one-time-use.
- **Account lockout:** `LoginAttemptService` tracks failed attempts in Redis; after threshold, locks the account for a configurable window. Redis key: `login_attempts:{email}`.

### Security Filter Chains

`SecurityConfig` configures **4 separate filter chains** (Spring Security `@Order`, lower number = higher priority):
1. **Stripe webhook chain** (`@Order(1)`) — STATELESS, CSRF disabled, signature verified in service; matches `/payment/stripe/webhook`
2. **SBPS webhook chain** (`@Order(2)`) — STATELESS, CSRF disabled, `res_sps_hashcode` verified in service; matches `/payment/sbpayment/result`
3. **Withdrawal webhook chain** (`@Order(3)`) — STATELESS, CSRF disabled, no Spring Security auth; matches `/withdraw/webhook` (HMAC verified in controller)
4. **Main chain** (default order) — form login + OAuth2 + session management; all other app routes

### Transaction Filtering & Pagination

`TransactionSpec` builds JPA `Specification` predicates for type (`DEPOSIT`/`WITHDRAW`/`TRANSFER`) and date range. `TransactionRepository` extends `JpaSpecificationExecutor`. Controllers pass a `Pageable` (default 10 per page, ordered by `createdAt` DESC).
