# Wallet System

A server-rendered digital wallet application built with Spring Boot. Supports user registration and authentication, wallet top-ups via two payment gateways, peer-to-peer transfers, and bank withdrawals with asynchronous webhook confirmation.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 4.0.5 · Java 17 · Spring Security |
| Database | PostgreSQL 14+ · Spring Data JPA |
| Cache / Session | Redis |
| Frontend | Thymeleaf · inline CSS |
| Payments | Stripe · SoftBank Payment Service (SBPS) |
| Auth | Form login · Google OAuth2/OIDC · Email OTP |
| Observability | Logback + MDC TraceId · Prometheus Actuator |

## Features

- **Authentication** — Register / login with email + password, Google OAuth2, email OTP 2FA, password reset via email
- **Wallet** — Each account has one wallet with a unique 12-char code; real-time balance display
- **Deposit** — Top up via Stripe (credit card, 3DS) or SoftBank Payment Service (link-type)
- **Withdraw** — Async bank transfer request with webhook confirmation and automatic timeout refund
- **Transfer** — Instant peer-to-peer transfer by wallet code
- **Transaction history** — Filterable by type and date range, paginated
- **Profile** — Edit name, nickname, phone, bio, birthday; upload avatar (JPEG/PNG/GIF/WEBP)
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

# Optional — required for email OTP / password reset
export MAIL_HOST=smtp.gmail.com
export MAIL_PORT=587
export MAIL_USERNAME=your@gmail.com
export MAIL_PASSWORD=your-app-password
```

### 3. Run

```bash
./mvnw spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080)

The schema is applied automatically on first startup via `src/main/resources/schema.sql`.

## API Documentation

Swagger UI is available at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) after starting the app.

The OpenAPI spec is at `src/main/resources/static/openapi.yaml`.

## Running Tests

Tests use an H2 in-memory database — no external services needed.

```bash
# All tests
./mvnw test

# Single class
./mvnw test -Dtest=WalletServiceTest

# Single method
./mvnw test -Dtest=WalletServiceTest#depositSuccess
```

## Project Structure

```
src/
├── main/java/.../wallet_system/
│   ├── auth/        # Registration, login, OAuth2, OTP, password reset, profile
│   ├── wallet/      # Wallet entity, deposit/withdraw/transfer logic, webhook
│   ├── transaction/ # Transaction entity, filtering, timeout job
│   ├── payment/     # Stripe and SBPS payment controllers + services
│   └── config/      # Security, Web MVC, TraceId filter
├── main/resources/
│   ├── templates/   # Thymeleaf HTML templates
│   ├── schema.sql   # DDL (CREATE TABLE IF NOT EXISTS + additive migrations)
│   ├── messages*.properties  # i18n bundles (EN / JA / ZH-TW)
│   └── static/openapi.yaml
└── test/
    ├── java/        # Unit tests (*Test.java) + MockMvc integration tests (*IT.java)
    └── resources/application-test.yaml
```

## Withdrawal Flow

Withdrawals are processed asynchronously:

1. Balance is deducted immediately; transaction status → `REQUEST_COMPLETED`
2. A request is sent to the mock bank API
3. The bank calls back `POST /withdraw/webhook` with `result=SUCCESS` or `result=FAILED`
4. On success → `COMPLETED`; on failure → `FAILED` and balance is refunded
5. A scheduled job (`TransactionTimeoutJob`) runs every 60 s and refunds any `REQUEST_COMPLETED` transaction older than 5 minutes

## Health & Metrics

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Application health |
| `GET /actuator/info` | Build info |
| `GET /actuator/prometheus` | Prometheus metrics |
