# Wallet System

A server-rendered digital wallet application built with Spring Boot. Supports user registration and authentication, wallet top-ups via two payment gateways, peer-to-peer transfers, and bank withdrawals with asynchronous webhook confirmation.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 4.0.5 · Java 17 · Spring Security |
| Database | PostgreSQL 14+ · Spring Data JPA · Flyway |
| Cache / Session | Redis |
| Frontend | Thymeleaf · inline CSS |
| Payments | Stripe · SoftBank Payment Service (SBPS) |
| Auth | Form login · Google OAuth2/OIDC · Email OTP |
| Observability | Logback + MDC TraceId · Prometheus Actuator · Grafana + Loki |

## Features

- **Authentication** — Register / login with email + password, Google OAuth2, email OTP 2FA, password reset via email, brute-force lockout (5 failures → 15 min)
- **Wallet** — Each account has one wallet with a unique 12-char code; real-time balance with pessimistic locking to prevent concurrent corruption
- **Deposit** — Top up via Stripe (credit card, 3DS) or SoftBank Payment Service (link-type); async PENDING → COMPLETED flow
- **Withdraw** — Async bank transfer with HMAC-SHA256 signed webhook confirmation and automatic timeout refund
- **Transfer** — Instant peer-to-peer transfer by wallet code; deadlock-safe dual-lock ordering
- **Transaction history** — Filterable by type and date range, paginated
- **Profile** — Edit name, nickname, phone, bio, birthday; upload avatar (JPEG/PNG/GIF/WEBP, max 2 MB)
- **i18n** — English / Japanese / Traditional Chinese via `Accept-Language` header

## Prerequisites

- Java 17+
- PostgreSQL 14+ (default: `localhost:5432`, DB: `wallet_db`, user: `postgres`)
- Redis (default: `localhost:6379`)
- Maven Wrapper included — no local Maven required

## Quick Start

### 1. Create the database

```sql
CREATE DATABASE wallet_db;
```

### 2. Set environment variables

```bash
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
export STRIPE_SECRET_KEY=sk_test_...
export STRIPE_PUBLISHABLE_KEY=pk_test_...
export STRIPE_WEBHOOK_SECRET=whsec_...

# SBPS (SoftBank Payment Service)
export PAYMENT_MERCHANT_ID=...
export PAYMENT_SERVICE_ID=...
export PAYMENT_HASH_KEY=...

# Shared secret between wallet_system and mock-bank for webhook signature
export WITHDRAW_WEBHOOK_SECRET=your-random-secret

# Optional — required for email OTP / password reset
export MAIL_HOST=smtp.gmail.com
export MAIL_PORT=587
export MAIL_USERNAME=your@gmail.com
export MAIL_PASSWORD=your-app-password
```

See `.env.example` at the repo root for a full template.

### 3. Run

```bash
./mvnw spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080)

The schema is applied automatically on first startup via Flyway (`src/main/resources/db/migration/V1__init_schema.sql`). To add a schema change, create a new `V{n}__description.sql` file — never edit existing migrations.

## Docker Compose

The easiest way to run the full stack (PostgreSQL, Redis, mock-bank, Loki, Grafana, Promtail, and the wallet app):

```bash
# From repo root — copy and fill .env.example → wallet_system/.env first
cd wallet_system
docker compose up --build
```

Grafana is available at [http://localhost:3000](http://localhost:3000) (default admin/admin). Pre-configured with Loki as a log data source.

## API Documentation

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

The OpenAPI spec is at `src/main/resources/static/openapi.yaml` (spec-first, maintained manually).

## Running Tests

Tests use an H2 in-memory database — no external services needed.

```bash
# All tests
./mvnw test

# Single class
./mvnw test -Dtest=WalletServiceTest

# Single method
./mvnw test -Dtest=WalletServiceTest#deposit_validAmount_increasesBalance
```

186 tests across 16 test classes. Unit tests use `@ExtendWith(MockitoExtension.class)`; integration tests use `@WebMvcTest` + `@MockitoBean`.

## Project Structure

```
src/
├── main/java/.../wallet_system/
│   ├── auth/        # Registration, login, OAuth2, OTP, password reset, profile
│   ├── wallet/      # Wallet entity, deposit/withdraw/transfer logic, webhook
│   ├── transaction/ # Transaction entity, filtering, timeout job
│   ├── payment/     # Stripe and SBPS payment controllers + services
│   └── config/      # Security (4 filter chains), Web MVC, TraceId filter
├── main/resources/
│   ├── templates/   # Thymeleaf HTML templates
│   ├── db/migration/V1__init_schema.sql  # Flyway schema (members, wallets, transactions)
│   ├── messages*.properties  # i18n bundles (EN / JA / ZH-TW)
│   └── static/openapi.yaml
└── test/
    ├── java/        # Unit tests (*Test.java) + MockMvc integration tests (*IT.java)
    └── resources/application-test.yaml
```

## Withdrawal Flow

Withdrawals are processed asynchronously:

1. Balance is deducted immediately; transaction status → `REQUEST_COMPLETED`
2. A request is sent to the mock bank API with an HMAC-SHA256 signed callback URL
3. The bank calls back `POST /withdraw/webhook`; signature is verified before processing
4. On `SUCCESS` → `COMPLETED`; on `FAILED` → `FAILED` and balance is refunded
5. `TransactionTimeoutJob` runs every 60 s and refunds any `REQUEST_COMPLETED` transaction older than 5 minutes

## Security Highlights

| Mechanism | Details |
|-----------|---------|
| Webhook signature | HMAC-SHA256 with constant-time comparison (`MessageDigest.isEqual`) |
| Stripe webhook | SDK-verified `Stripe-Signature` header |
| Pessimistic locking | `SELECT ... FOR UPDATE` on all balance-modifying operations |
| Deadlock prevention | Transfer acquires both wallet locks in ascending UUID order |
| Brute-force protection | Account lockout via Redis after 5 failed login attempts |
| Path traversal | Avatar uploads validated with extension allowlist + `normalize().startsWith()` boundary check |
| CSRF | Enabled on main chain; disabled only for server-to-server webhook chains |

## Health & Metrics

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Application and dependency health |
| `GET /actuator/info` | Build info |
| `GET /actuator/prometheus` | Prometheus metrics |
