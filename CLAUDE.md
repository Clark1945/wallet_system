# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Monorepo Structure

Two Spring Boot services:
- **`wallet_system/`** — main wallet application (port 8080); detailed guidance in `wallet_system/CLAUDE.md`
- **`mock-bank/`** — simulated bank withdrawal endpoint (port 8081)

## Build & Run Commands

All commands run from within the respective service directory (`cd wallet_system` or `cd mock-bank`).

```bash
# Full stack (requires .env with GOOGLE_*, STRIPE_*, MAIL_* credentials)
docker compose up --build

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
3. `WithdrawWebhookController` receives the callback and sets `Transaction.status = COMPLETED`
4. **Timeout safety:** `TransactionTimeoutJob` (`@Scheduled(fixedDelay = 60_000)`) runs every 60 seconds and refunds any `PENDING` or `REQUEST_COMPLETED` transaction older than 5 minutes (sets status `FAILED`, restores balance)

Transaction status lifecycle: `PENDING` → `REQUEST_COMPLETED` → `COMPLETED` (or `FAILED` on timeout)

The webhook endpoint (`/withdraw/webhook`) has its own Spring Security filter chain: STATELESS, no CSRF, no auth required.

## mock-bank

Single endpoint: `POST /api/withdraw` accepts `{ transactionId, amount, bankCode, bankAccount, callbackUrl }`. Returns `200 OK` synchronously, then calls `callbackUrl` after random delay. No database — all in-memory. Configured via `mock-bank/src/main/resources/application.properties`.

## wallet_system Key Architecture

See `wallet_system/CLAUDE.md` for complete details. Highlights not covered there:

### Authentication Flows

Three login methods beyond form login and Google OAuth2:
- **Email OTP:** `OtpService` generates a 6-digit code, stores it in Redis (5-minute TTL), sends via `EmailService`. `OtpController` verifies and completes login.
- **Password reset:** `PasswordResetService` generates a UUID token stored in Redis (15-minute TTL), emails a reset link. Token is one-time-use.
- **Account lockout:** `LoginAttemptService` tracks failed attempts in Redis; after threshold, locks the account for a configurable window. Redis key: `login_attempts:{email}`.

### Security Filter Chains

`SecurityConfig` configures **4 separate filter chains** (Spring Security `@Order`):
1. **Main chain** — form login + OAuth2 + session management; all app routes
2. **Stripe webhook chain** — STATELESS, CSRF disabled, signature verified in service
3. **SBPS webhook chain** — STATELESS, CSRF disabled, `res_sps_hashcode` verified in service
4. **Withdrawal webhook chain** — STATELESS, CSRF disabled, no auth

### Transaction Filtering & Pagination

`TransactionSpec` builds JPA `Specification` predicates for type (`DEPOSIT`/`WITHDRAW`/`TRANSFER`) and date range. `TransactionRepository` extends `JpaSpecificationExecutor`. Controllers pass a `Pageable` (default 10 per page, ordered by `createdAt` DESC).
