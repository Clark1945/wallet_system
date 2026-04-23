# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run the application (requires PostgreSQL and env vars below)
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

These are required at runtime (tests do not need them):
```
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
STRIPE_SECRET_KEY=...
STRIPE_PUBLISHABLE_KEY=...
STRIPE_WEBHOOK_SECRET=...
```

SBPS credentials are hardcoded in `application.yaml` (test sandbox values — do not use in production).

## Architecture Overview

**Stack:** Spring Boot 4.0.5 · Java 17 · Spring Security · Spring Data JPA · PostgreSQL · Thymeleaf · Lombok

**Base package:** `org.side_project.wallet_system`

**Module layout** under `src/main/java/.../wallet_system/`:
- `auth/` — `AuthController`, `AuthService`, `Member` (JPA entity), `MemberRepository`, `AuthProvider` (enum), `CustomUserDetails`, `CustomOAuth2User`, `CustomOAuth2UserService`, `LoginSuccessHandler`, `ResponseDto`, `ProfileController`, `ProfileService` (avatar upload + profile field updates)
- `wallet/` — `WalletController`, `WalletService`, `Wallet` (JPA entity), `WalletRepository`
- `transaction/` — `Transaction` (JPA entity), `TransactionRepository`, `TransactionType` (enum), `TransactionSpec` (JPA Criteria filtering)
- `payment/` — `StripePaymentController`, `StripePaymentService`, `SBPaymentController`, `SBPaymentService`, `SBPaymentRequest`
- `config/` — `SecurityConfig`, `WebConfig` (`AcceptHeaderLocaleResolver` + `/uploads/**` resource handler), `TraceIdFilter`

This is a **server-rendered MVC app**, not a REST API. All controllers are `@Controller` (not `@RestController`) and return Thymeleaf view names. Flash attributes carry success/error messages between redirects.

### Authentication & Session

Spring Security handles all authentication via `SecurityConfig`. Five login methods are supported:

- **Form login:** email + password; `BCryptPasswordEncoder` compares credentials; principal is `CustomUserDetails`
- **Google OAuth2/OIDC:** `CustomOAuth2UserService` calls `AuthService.findOrCreateGoogleMember()` and wraps the result in `CustomOAuth2User`
- **Email OTP:** `OtpService` generates a 6-digit code stored in Redis (5-minute TTL) and sends it via `EmailService`. `OtpController` verifies and completes login.
- **Password reset:** `PasswordResetService` generates a UUID token stored in Redis (15-minute TTL), emails a reset link. Token is single-use.
- **Account lockout:** `LoginAttemptService` tracks failed attempts in Redis (key `login_attempts:{email}`); after 5 failures, account locks for 15 minutes.

`LoginSuccessHandler` fires after both form/OAuth2 flows — it extracts `memberId` (UUID) and `memberName` from the principal and stores them in `HttpSession` as plain strings, then redirects to `/dashboard`. Controllers pull `memberId`/`memberName` directly from `HttpSession` attributes (no `@SessionAttribute`).

Public URLs (login, register, OpenAPI, actuator) are permitted in `SecurityConfig`. All others require authentication. Logout at `/logout` invalidates the session and deletes `JSESSIONID`.

`Member.authProvider` (`LOCAL` or `GOOGLE`) distinguishes how the account was created; `password` is nullable for OAuth2 members. `UserDetailsService` bean filters for `LOCAL` auth only — OAuth2 members are not loaded through it.

### Member Entity

`Member` fields beyond the basics: `nickname`, `phone`, `bio`, `birthday` (LocalDate), `avatarPath` (relative path like `avatars/{uuid}.jpg`). All profile fields are nullable to allow partial updates.

### Wallet & Transaction Flow

- Each `Member` has exactly one `Wallet` (OneToOne). The wallet is created automatically during registration.
- `Wallet.walletCode` is a 12-char alphanumeric string generated via `SecureRandom` and used for peer-to-peer transfers.
- `WalletService` methods (`deposit`, `withdraw`, `transfer`) are `@Transactional`. They throw `IllegalArgumentException` with i18n message keys on invalid input (negative amounts, insufficient balance, self-transfer, unknown wallet code).
- `Transaction` records every operation, with nullable `fromWalletId`/`toWalletId` depending on `TransactionType` (DEPOSIT / WITHDRAW / TRANSFER).
- Transaction history supports filtering by `TransactionType` and date range via `TransactionSpec` (JPA Criteria API), plus server-side pagination.

**Async withdrawal flow:**
1. `WalletService.withdraw()` deducts balance and creates a `Transaction` with status `REQUEST_COMPLETED`, then fires `HttpClient.sendAsync()` to `POST mock-bank:8081/api/withdraw`.
2. mock-bank responds `200 OK` immediately, then calls back `POST /withdraw/webhook` after a 3–8 second delay.
3. `WithdrawWebhookController` processes `{"transactionId":"...", "result":"SUCCESS"/"FAIL"}`: SUCCESS → `COMPLETED`; FAIL → `FAILED` + balance refunded.
4. `TransactionTimeoutJob` (`@Scheduled(fixedDelay = 60_000)`) runs every 60 seconds; any `PENDING` or `REQUEST_COMPLETED` transaction older than 5 minutes is set to `FAILED` and balance is refunded.

Transaction status lifecycle: `PENDING` → `REQUEST_COMPLETED` → `COMPLETED` (or `FAILED` on webhook FAIL / timeout).

### Payment Gateway Integrations

Two external payment gateways integrate with `WalletService.deposit()` for wallet top-ups:

**Stripe** (`payment/StripePaymentService`, `payment/StripePaymentController`):
- Creates a PaymentIntent (JPY, no sub-unit conversion). Returns `clientSecret` to `stripe-checkout.html` for Stripe.js with automatic 3DS.
- Webhook at `/payment/stripe/webhook` verifies `Stripe-Signature` then calls `deposit()` on `payment_intent.succeeded`. Uses in-memory `processedIntents` set to guard against duplicate webhook delivery.

**SBPS / SoftBank Payment Service** (`payment/SBPaymentService`, `payment/SBPaymentController`):
- Link-type integration: builds an `SBPaymentRequest` with a SHA-1 hashcode (all fields concatenated + `hashKey`), posts a form to SBPS gateway (`sb-payment.html`). `request_date` must be in JST.
- Result CGI callback at `/payment/sbpayment/result` verifies `res_result=OK`, looks up the in-memory `pendingOrders` map by `order_id`, then calls `deposit()`.
- Both gateways use in-memory idempotency state (acceptable for this test/side-project environment).

### Profile & File Uploads

- `ProfileService.updateProfile()` handles name, nickname, phone, bio, birthday (in `auth/` package).
- `ProfileService.updateAvatar()` accepts JPEG/PNG/GIF/WEBP (max 2 MB); validates via MIME `contentType` (not file extension); stores files at `uploads/avatars/{memberId}.{ext}` (overwrites on extension change).
- Upload directory configured via `app.upload.dir` property (defaults to `uploads/` relative path); multipart limits set to 10 MB in `application.yaml`.
- `WebConfig` registers a `/uploads/**` resource handler serving from `file:{uploadDir}` so stored files are accessible as static resources.
- Controllers catch `IllegalArgumentException` from services and catch generic `Exception` separately for file I/O failures, mapping each to different i18n error keys.

### Error Handling Pattern

Services throw `IllegalArgumentException` with an i18n message key as the message. Controllers call:
```java
messageSource.getMessage(e.getMessage(), null, e.getMessage(), locale)
```
The fallback (third arg) returns the raw key if no translation exists. This pattern is used across all controllers.

### Internationalization

`AcceptHeaderLocaleResolver` with supported locales: `en`, `ja`, `zh-TW` (default: `en`). Message bundles are in `src/main/resources/messages*.properties`. All user-facing strings — including error messages thrown in services — use message keys resolved via `MessageSource`.

### Observability

- **TraceIdFilter** (`OncePerRequestFilter`, `HIGHEST_PRECEDENCE`): reads or generates a UUID from the `X-Trace-Id` request header, stores it in SLF4J MDC under key `traceId`, echoes it on the response header, and clears MDC in a finally block.
- **Logging:** `logback-spring.xml` includes `traceId` in every log line via `[%X{traceId:-no-trace}]`. File appender writes rolling daily logs to `logs/` (30-day retention). App package logs at DEBUG; root at INFO. Test profile disables the file appender.
- **Actuator + Prometheus:** `/actuator/health`, `/actuator/info`, `/actuator/prometheus` are exposed and permit-listed in SecurityConfig.

### Database

- **Runtime:** PostgreSQL (`localhost:5432/wallet_db`, user: `postgres`)
- **Tests:** H2 in-memory with `ddl-auto: create-drop`; Flyway is disabled for tests (`spring.flyway.enabled: false` in `application-test.yaml`) — Hibernate recreates the schema from entities.
- **DDL:** Managed by Flyway. Migrations live in `src/main/resources/db/migration/` and run automatically on startup. `ddl-auto: none`. Current baseline: `V1__init_schema.sql` (creates `members`, `wallets`, `transactions` tables with indexes).
- To add a schema change, create a new `V{n}__description.sql` file; never edit existing migrations.

### OpenAPI / Swagger

Spec-first approach: `src/main/resources/static/openapi.yaml` is the source of truth (not auto-generated). Swagger UI is served at `/swagger-ui.html` and loads from `/openapi.yaml`. The spec documents session-cookie auth (`JSESSIONID`) and all endpoints across the Auth and Wallet tags.

### Thymeleaf Templates

Templates live in `src/main/resources/templates/`. All use inline CSS, support Thymeleaf expressions (`th:text`, `th:href`, `@{}`, `#{}`), include CSRF tokens on POST forms, and display flash attributes for success/error feedback. Key templates: `login.html`, `register.html`, `dashboard.html` (transaction history with filter form and pagination), `deposit.html`, `withdraw.html`, `transfer.html`, `profile.html` (profile fields + avatar upload), `stripe-checkout.html` (Stripe.js payment form), `sb-payment.html` (auto-submitting form to SBPS gateway).

### Test Conventions

- `*Test.java` — unit tests with Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`)
- `*IT.java` — integration tests with MockMvc (`@WebMvcTest` + `SecurityConfig`, `@MockitoBean` for services)
- `*Tests.java` — application context test (`@SpringBootTest`)
- Integration tests use `@ActiveProfiles("test")` (triggers H2 config from `application-test.yaml`)
- Controller tests validate redirects, flash attributes, and model attributes via MockMvc result matchers
- `WalletControllerIT` sets up session state via `MockHttpSession` with a `memberId` attribute
- `ProfileServiceTest` uses `@TempDir` (JUnit 5) and `ReflectionTestUtils.setField()` to inject `uploadDir`
- `ProfileControllerIT` tests multipart endpoints with `MockMultipartFile`
