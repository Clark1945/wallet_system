# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run the application (requires PostgreSQL, Redis, and env vars below)
./mvnw spring-boot:run

# Build (produces jar in target/)
./mvnw clean package

# Compile only
./mvnw compile

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=AuthServiceTest

# Run a specific test method
./mvnw test -Dtest=AuthServiceTest#registerSuccess
```

No separate lint step; the compiler plugin handles annotation processing via Lombok.

**Rule: run `./mvnw test` after every code change and fix all failures before committing.**

### Required Environment Variables

```
# Database
DB_URL=jdbc:postgresql://localhost:5432/wallet_db
DB_USERNAME=postgres
DB_PASSWORD=...

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=...

# Google OAuth2
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...

# Stripe
STRIPE_SECRET_KEY=...
STRIPE_PUBLISHABLE_KEY=...
STRIPE_WEBHOOK_SECRET=...

# Mail (defaults to smtp.gmail.com:587)
MAIL_HOST=...
MAIL_PORT=...
MAIL_USERNAME=...
MAIL_PASSWORD=...

# Withdrawal webhook HMAC
WITHDRAW_WEBHOOK_SECRET=...

# SBPS payment gateway
PAYMENT_MERCHANT_ID=...
PAYMENT_SERVICE_ID=...
PAYMENT_HASH_KEY=...
```

## Architecture Overview

**Stack:** Spring Boot 4.0.5 · Java 17 · Spring Security · Spring Data JPA · PostgreSQL · Redis · Thymeleaf · Lombok

**Base package:** `org.side_project.wallet_system`

**Module layout** under `src/main/java/.../wallet_system/`:
- `auth/controller/` — `AuthPageController` (GET pages: login, register, forgot-password), `AuthController` (POST actions + OTP endpoints, all delegate to `AuthFlowService`), `ProfileController`
- `auth/email/` — `EmailService` interface; `JavaMailEmailService` (active profile) and `NoOpEmailService` (test/no-mail profile)
- `auth/oauth/` — `CustomOAuth2User`, `CustomOAuth2UserService`, `CustomUserDetails`, `LoginSuccessHandler`
- `auth/objects/` — `Member` (JPA entity), `MemberStatus` (enum: `PENDING`/`ACTIVE`), `AuthProvider` (enum: `LOCAL`/`GOOGLE`), `OtpType` (enum: `REGISTER`/`LOGIN`), `RegisterRequest`, `ForgotPasswordRequest`, `ResetPasswordRequest`, `ResponseDto`
- `auth/repository/` — `MemberRepository`
- `auth/service/` — `AuthFlowService` (orchestrates all auth flows), `AuthService`, `OtpService`, `PasswordResetService`, `LoginAttemptService`, `ProfileService`
- `wallet/` — `WalletController`, `WalletService`, `Wallet` (JPA entity), `WalletRepository`, `WalletNotFoundException`, `WithdrawWebhookController`
- `transaction/` — `Transaction` (JPA entity), `TransactionRepository`, `TransactionType` (enum: `DEPOSIT`/`WITHDRAW`/`TRANSFER`), `TransactionStatus` (enum: `PENDING`/`REQUEST_COMPLETED`/`COMPLETED`/`FAILED`), `TransactionSpec` (JPA Criteria filtering), `TransactionTimeoutJob`
- `payment/` — `StripePaymentController`, `StripePaymentService`, `SBPaymentController`, `SBPaymentService`, `SBPaymentRequest`
- `config/` — `SecurityConfig`, `AppConfig` (`@EnableAsync`, `HttpClient` and `StripeClient` beans), `WebConfig` (`AcceptHeaderLocaleResolver` + `/uploads/**` resource handler), `RedisConfig`, `TraceIdFilter`, `GlobalExceptionHandler` (`@ControllerAdvice`), `SessionConstants` (session key constants), `SessionUtils` (helper to extract `UUID` memberId from session)

This is a **server-rendered MVC app**, not a REST API. All controllers are `@Controller` and return Thymeleaf view names. Flash attributes carry success/error messages between redirects.

### Authentication & Session

Spring Security handles all authentication via `SecurityConfig`. Five login methods:

- **Form login:** email + password; `BCryptPasswordEncoder`; principal is `CustomUserDetails`
- **Google OAuth2/OIDC:** `CustomOAuth2UserService` calls `AuthService.findOrCreateGoogleMember()` and wraps the result in `CustomOAuth2User`
- **Email OTP (login):** `AuthService.sendLoginOtp()` generates a code via `OtpService` (10-minute TTL in Redis), sends via `EmailService`. Verified via `AuthController` → `AuthFlowService.verifyLoginOtp()`.
- **Password reset:** `PasswordResetService` generates a UUID token (15-minute TTL in Redis), emails a reset link. Token is single-use; the mid+token is temporarily stored in session during the reset form flow.
- **Account lockout:** `LoginAttemptService` tracks failed attempts in Redis (key `login_attempts:{email}`); after 5 failures, account locks for 15 minutes.

`LoginSuccessHandler` fires after both form/OAuth2 flows — it stores `memberId` (UUID string) and `memberName` in `HttpSession` via `SessionConstants.MEMBER_ID` / `SessionConstants.MEMBER_NAME`, then redirects to `/dashboard`. Controllers retrieve these via `SessionUtils.getMemberId(session)`.

`Member.authProvider` (`LOCAL` or `GOOGLE`) distinguishes account origin; `password` is nullable for OAuth2 members. `UserDetailsService` bean filters for `LOCAL` + `ACTIVE` members only.

### Registration OTP Flow

Registration is a two-step process gated by email OTP:

1. `POST /register` → `AuthFlowService.register()` → `AuthService.initiateRegistration()` creates a `PENDING` member (no wallet yet), sends a 6-digit OTP via `EmailService`, generates an OTP flow token in Redis, redirects to `/register/otp?token=...`
2. `POST /register/otp` → `AuthFlowService.verifyRegistrationOtp()` → `AuthService.verifyAndActivate()` verifies the OTP, sets `member.status = ACTIVE`, and creates the `Wallet`. Up to 5 failed attempts before the token is locked.

Stale `PENDING` members are deleted and recreated if the same email re-registers.

### OTP Redis Key Patterns

- OTP code: `member:{memberId}:{loginToken|registerToken}:{otp}` (TTL: 10 min, deleted on verify)
- OTP flow token: `otp_token:{token}` (TTL: 5 min, maps to `OtpToken` object with memberId + type)
- OTP attempts: `otp_attempts:{token}` (TTL: 10 min, incremented per failed attempt)
- Login attempts: `login_attempts:{email}` (managed by `LoginAttemptService`)

### Member Entity

Fields beyond basics: `nickname`, `phone`, `bio`, `birthday` (LocalDate), `avatarPath` (relative path like `avatars/{uuid}.jpg`), `status` (MemberStatus), `lastLoginAt`. All profile fields are nullable.

### Wallet & Transaction Flow

- Each `Member` has exactly one `Wallet` (OneToOne), created when member is activated (not at registration).
- `Wallet.walletCode` is a 12-char alphanumeric string generated via `SecureRandom` for peer-to-peer transfers.
- `WalletService` methods (`deposit`, `withdraw`, `transfer`) are `@Transactional` with pessimistic locking. They throw `IllegalArgumentException` with i18n message keys on invalid input.
- `transfer()` acquires pessimistic locks on both wallets in ascending `Wallet.id` order (via `WalletRepository.findByIdsForUpdate`) to prevent deadlocks under concurrent transfers.
- `Transaction` records every operation with nullable `fromWalletId`/`toWalletId` depending on type.
- Transaction history supports filtering by `TransactionType` and date range via `TransactionSpec` (JPA Criteria API), plus server-side pagination (default 10 per page, `createdAt` DESC).

**Async withdrawal flow:**
1. `WalletService.withdraw()` deducts balance, creates a `Transaction` with status `REQUEST_COMPLETED`, fires `HttpClient.sendAsync()` to `POST mock-bank:8081/api/withdraw`.
2. mock-bank responds `200 OK` immediately, then calls back `POST /withdraw/webhook` after a 3–8 second delay.
3. `WithdrawWebhookController` verifies `X-Webhook-Signature: sha256=<hex>` (HMAC-SHA256 of raw request body, key = `WITHDRAW_WEBHOOK_SECRET`), then processes `{"transactionId":"...", "result":"SUCCESS"/"FAIL"}`: SUCCESS → `COMPLETED`; FAIL → `FAILED` + balance refunded.
4. `TransactionTimeoutJob` (`@Scheduled(fixedDelay = 60_000)`) runs every 60 seconds; any `PENDING` or `REQUEST_COMPLETED` transaction older than 5 minutes is set to `FAILED` and balance is refunded.

### Payment Gateway Integrations

**Stripe** (`payment/StripePaymentService`, `payment/StripePaymentController`):
- Creates a PaymentIntent (JPY, no sub-unit conversion). Returns `clientSecret` to `stripe-checkout.html`.
- Webhook at `/payment/stripe/webhook` verifies `Stripe-Signature` then calls `deposit()` on `payment_intent.succeeded`. In-memory `processedIntents` set guards duplicate delivery.

**SBPS / SoftBank Payment Service** (`payment/SBPaymentService`, `payment/SBPaymentController`):
- Link-type integration: builds an `SBPaymentRequest` with a SHA-1 hashcode (fields concatenated + `hashKey`), posts a form to SBPS gateway. `request_date` must be in JST.
- Result CGI callback at `/payment/sbpayment/result` verifies `res_result=OK`, looks up in-memory `pendingOrders` by `order_id`, then calls `deposit()`.

### Error Handling

Services throw `IllegalArgumentException` with an i18n message key. Controllers resolve it:
```java
messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale)
```
The fallback returns the raw key if no translation exists.

`GlobalExceptionHandler` (`@ControllerAdvice`) handles cross-cutting cases:
- `ConstraintViolationException` → redirects to referer with flash error
- `NoResourceFoundException` → 404
- `Exception` → logs the error, redirects to `/`

### Internationalization

`AcceptHeaderLocaleResolver` with supported locales: `en`, `ja`, `zh-TW` (default: `en`). Message bundles: `src/main/resources/messages*.properties`.

### Profile & File Uploads

- `ProfileService.updateProfile()` handles name, nickname, phone, bio, birthday.
- `ProfileService.updateAvatar()` accepts JPEG/PNG/GIF/WEBP (max 2 MB); validates via MIME `contentType`; stores at `uploads/avatars/{memberId}.{ext}`.
- Upload directory configured via `app.upload.dir` (defaults to `uploads/`). `WebConfig` serves `/uploads/**` from `file:{uploadDir}`.

### Security Filter Chains

`SecurityConfig` configures **4 separate filter chains** (`@Order`, lower = higher priority):
1. **Stripe webhook** (`@Order(1)`) — STATELESS, CSRF disabled; matches `/payment/stripe/webhook`
2. **SBPS webhook** (`@Order(2)`) — STATELESS, CSRF disabled; matches `/payment/sbpayment/result`
3. **Withdrawal webhook** (`@Order(3)`) — STATELESS, CSRF disabled, no Spring Security auth; matches `/withdraw/webhook` (HMAC-SHA256 verified in `WithdrawWebhookController`)
4. **Main chain** (default) — form login + OAuth2 + session management; all other routes

### Observability

- **TraceIdFilter** (`OncePerRequestFilter`, `HIGHEST_PRECEDENCE`): reads/generates UUID from `X-Trace-Id`, stores in MDC as `traceId`, echoes on response.
- **Logging:** `logback-spring.xml` includes `traceId` in every line. Rolling daily logs in `logs/` (30-day retention). Test profile disables file appender.
- **Actuator + Prometheus:** `/actuator/health`, `/actuator/info`, `/actuator/prometheus` exposed and permit-listed.

### Database

- **Runtime:** PostgreSQL; connection via `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` env vars.
- **Tests:** H2 in-memory with `ddl-auto: create-drop`; Flyway disabled (`spring.flyway.enabled: false` in `application-test.yaml`).
- **DDL:** Flyway manages migrations in `src/main/resources/db/migration/`. Never edit existing migrations — add a new `V{n}__description.sql`.

### OpenAPI / Swagger

Spec-first: `src/main/resources/static/openapi.yaml` is the source of truth. Swagger UI at `/swagger-ui.html`.

### Test Conventions

- `*Test.java` — unit tests (`@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`)
- `*IT.java` — integration tests (`@WebMvcTest` + `SecurityConfig`, `@MockitoBean` for services)
- `*Tests.java` — application context test (`@SpringBootTest`)
- All tests use `@ActiveProfiles("test")` (H2 + disabled Flyway)
- Controller ITs validate redirects, flash attributes, and model attributes via MockMvc result matchers
- `WalletControllerIT` sets up session state via `MockHttpSession` with a `memberId` attribute
- `ProfileServiceTest` uses `@TempDir` and `ReflectionTestUtils.setField()` to inject `uploadDir`
