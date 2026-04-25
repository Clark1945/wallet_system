# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Monorepo Structure

Three Spring Boot services:
- **`wallet_system/`** — main wallet application (port 8080); detailed guidance in `wallet_system/CLAUDE.md`
- **`mock-bank/`** — simulated bank withdrawal endpoint (port 8081)
- **`payment-service/`** — Stripe + SBPS payment gateway handler (port 8082)

## Build & Run Commands

```bash
# Full stack — run from repo root
# Requires wallet_system/.env (copy from .env.example and fill in credentials)
# Starts: PostgreSQL, Redis, mock-bank, wallet app, payment-service, Loki, Promtail, Grafana
docker compose up --build
# wallet app: http://localhost:8080  |  Grafana: http://localhost:3000

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

## Deposit Flow (Cross-Service)

Deposits span wallet_system and payment-service:

1. User POSTs to `/deposit` → `WalletController` calls `PaymentTokenService.createToken()` which stores `{memberId, amount, method}` in Redis (15-minute TTL) under key `payment_token:{uuid}` and returns the token UUID.
2. User is redirected to `payment-service:8082/payment/{stripe|sbpayment}/checkout?token={token}`.
3. payment-service calls `GET wallet-service:8080/internal/token/{token}` to validate and consume the token (one-time use).
4. payment-service initiates the deposit via `POST /internal/deposit/initiate`, receives a `transactionId`, then interacts with the payment gateway (Stripe or SBPS).
5. On payment success (Stripe webhook or SBPS result CGI), payment-service calls `POST /internal/deposit/complete` or `/internal/deposit/complete-by-external`.
6. All `/internal/**` calls require the `X-Internal-Secret` header; wallet_system rejects with `401` if missing or wrong.

## Withdrawal Flow (Cross-Service)

1. User POSTs to `/withdraw` → `WalletService.withdraw()` deducts balance, creates `Transaction` with status `REQUEST_COMPLETED`, then calls `HttpClient.sendAsync()` to `POST mock-bank:8081/api/withdraw`.
2. mock-bank responds `200 OK` immediately, then fires a callback to `POST wallet_system:8080/withdraw/webhook` after a 3–8 second random delay.
3. `WithdrawWebhookController` verifies `X-Webhook-Signature: sha256=<hex>` (HMAC-SHA256 of raw body, key = `WITHDRAW_WEBHOOK_SECRET`), then sets `Transaction.status = COMPLETED` on SUCCESS or `FAILED` + refunds balance on FAIL.
4. **Timeout safety:** `TransactionTimeoutJob` (`@Scheduled(fixedDelay = 60_000)`) runs every 60 seconds and refunds any `PENDING` or `REQUEST_COMPLETED` transaction older than 5 minutes.

Transaction status lifecycle: `PENDING` → `REQUEST_COMPLETED` → `COMPLETED` (or `FAILED` on timeout)

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
