# Wallet System — Monorepo

A digital wallet platform built with Spring Boot. This repository contains two services:

| Module | Description | Port |
|--------|-------------|------|
| [`wallet_system/`](wallet_system/) | Main application — auth, wallet, payments | 8080 |
| [`mock-bank/`](mock-bank/) | Mock bank API for withdrawal webhook simulation | 8081 |

---

## Quick Start (Docker Compose)

```bash
# 1. Create a .env file with your credentials
cat > wallet_system/.env <<EOF
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
EOF

# 2. Build and start all services
cd wallet_system
docker compose up --build
```

This starts PostgreSQL, Redis, mock-bank, and the wallet app. The wallet app waits for the database and Redis to be healthy before starting.

Open [http://localhost:8080](http://localhost:8080)

---

## Services

### wallet_system

A server-rendered digital wallet application.

**Tech stack:** Spring Boot 4.0.5 · Java 17 · Spring Security · PostgreSQL 14 · Redis · Thymeleaf · Stripe · SBPS

**Features:**
- Register / login with email+password, Google OAuth2, email OTP 2FA, password reset
- Wallet with unique 12-char code and real-time balance
- Deposit via Stripe (credit card, 3DS) or SoftBank Payment Service (link-type)
- Async bank withdrawal with webhook confirmation and timeout refund
- Peer-to-peer transfer by wallet code
- Transaction history with filtering and pagination
- Profile editing and avatar upload
- i18n: English / Japanese / Traditional Chinese

See [`wallet_system/README.md`](wallet_system/README.md) for setup, environment variables, and test instructions.

### mock-bank

A lightweight Spring Boot stub that simulates a bank's withdrawal API.

**Endpoint:** `POST /api/withdraw`

```json
{
  "transactionId": "...",
  "amount": "1000",
  "bankCode": "012",
  "bankAccount": "1234567890",
  "callbackUrl": "http://app:8080/withdraw/webhook"
}
```

Responds immediately with `200 OK`, then after a random 3–8 second delay calls back `callbackUrl` with:

```json
{ "transactionId": "...", "result": "SUCCESS" }
```

---

## Withdrawal Flow

```
User                wallet_system              mock-bank
 │                       │                        │
 │── POST /withdraw ─────►│                        │
 │                        │── POST /api/withdraw ──►│
 │◄── redirect (pending) ─│                        │
 │                        │                        │  (3–8 s delay)
 │                        │◄── POST /withdraw/webhook ─│
 │                        │  { result: "SUCCESS" }  │
 │                        │                        │
 │    (balance confirmed)  │                        │
```

If the webhook does not arrive within 5 minutes, `TransactionTimeoutJob` automatically refunds the balance and marks the transaction as `FAILED`.

---

## Running Without Docker

Prerequisites: Java 17+, PostgreSQL 14+, Redis

```bash
# Start mock-bank
cd mock-bank
./mvnw spring-boot:run        # or: mvn spring-boot:run

# Start wallet app (separate terminal)
cd wallet_system
export GOOGLE_CLIENT_ID=...   # see wallet_system/README.md for full list
./mvnw spring-boot:run
```

---

## Repository Structure

```
.
├── mock-bank/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/mockbank/
│       ├── MockBankApplication.java
│       ├── WithdrawController.java
│       └── WithdrawRequest.java
└── wallet_system/
    ├── Dockerfile
    ├── docker-compose.yml      ← orchestrates all 4 services
    ├── README.md               ← wallet_system-specific docs
    ├── pom.xml
    └── src/
```
