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

Google OAuth2 login requires these at runtime (tests do not need them):
```
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
```

## Architecture Overview

**Stack:** Spring Boot 4.0.5 · Java 17 · Spring Security · Spring Data JPA · PostgreSQL · Thymeleaf · Lombok

**Base package:** `org.side_project.wallet_system`

**Module layout** under `src/main/java/.../wallet_system/`:
- `auth/` — `AuthController`, `AuthService`, `Member` (JPA entity), `MemberRepository`, `AuthProvider` (enum), `CustomUserDetails`, `CustomOAuth2User`, `CustomOAuth2UserService`, `LoginSuccessHandler`, `ResponseDto`
- `wallet/` — `WalletController`, `WalletService`, `Wallet` (JPA entity), `WalletRepository`
- `payment/` — `Transaction` (JPA entity), `TransactionRepository`, `TransactionType` (enum), `TransactionSpec` (JPA Criteria filtering)
- `config/` — `SecurityConfig`, `WebConfig` (`AcceptHeaderLocaleResolver`), `TraceIdFilter`

This is a **server-rendered MVC app**, not a REST API. All controllers are `@Controller` (not `@RestController`) and return Thymeleaf view names. Flash attributes carry success/error messages between redirects.

### Authentication & Session

Spring Security handles all authentication via `SecurityConfig`. Two login methods are supported:

- **Form login:** email + password; `BCryptPasswordEncoder` compares credentials; principal is `CustomUserDetails`
- **Google OAuth2/OIDC:** `CustomOAuth2UserService` calls `AuthService.findOrCreateGoogleMember()` and wraps the result in `CustomOAuth2User`

`LoginSuccessHandler` fires after both flows — it extracts `memberId` (UUID) and `memberName` from the principal and stores them in `HttpSession`, then redirects to `/dashboard`.

Public URLs (login, register, OpenAPI, actuator) are permitted in `SecurityConfig`. All others require authentication. Logout at `/logout` invalidates the session and deletes `JSESSIONID`.

`Member.authProvider` (`LOCAL` or `GOOGLE`) distinguishes how the account was created; `password` is nullable for OAuth2 members.

### Wallet & Transaction Flow

- Each `Member` has exactly one `Wallet` (OneToOne). The wallet is created automatically during registration.
- `Wallet.walletCode` is a 12-char alphanumeric string generated via `SecureRandom` and used for peer-to-peer transfers.
- `WalletService` methods (`deposit`, `withdraw`, `transfer`) are `@Transactional`. They throw `IllegalArgumentException` with i18n message keys on invalid input (negative amounts, insufficient balance, self-transfer, unknown wallet code).
- `Transaction` records every operation, with nullable `fromWalletId`/`toWalletId` depending on `TransactionType` (DEPOSIT / WITHDRAW / TRANSFER).
- Transaction history supports filtering by `TransactionType` and date range via `TransactionSpec` (JPA Criteria API), plus server-side pagination.

### Internationalization

`AcceptHeaderLocaleResolver` with supported locales: `en`, `ja`, `zh-TW` (default: `en`). Message bundles are in `src/main/resources/messages*.properties`. All user-facing strings — including error messages thrown in services — use message keys resolved via `MessageSource`.

### Observability

- **TraceIdFilter** (`OncePerRequestFilter`, `HIGHEST_PRECEDENCE`): reads or generates a UUID from the `X-Trace-Id` request header, stores it in SLF4J MDC under key `traceId`, echoes it on the response header, and clears MDC in a finally block. Log output includes the trace ID on every line.
- **Actuator + Prometheus:** `/actuator/health`, `/actuator/info`, `/actuator/prometheus` are exposed and permit-listed in SecurityConfig.

### Database

- **Runtime:** PostgreSQL (`localhost:5432/wallet_db`, user: `postgres`)
- **Tests:** H2 in-memory with `ddl-auto: create-drop`; SQL init is disabled for tests (Hibernate recreates schema from entities)
- **DDL:** `src/main/resources/schema.sql` runs on startup (`spring.sql.init.mode: always`). `ddl-auto: none` — schema is managed manually. The file includes idempotent `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` migration statements at the bottom.
- `script.sql` at the repo root is a standalone PostgreSQL DDL script for manual setup.

### OpenAPI / Swagger

Spec-first approach: `src/main/resources/static/openapi.yaml` is the source of truth. Swagger UI is served at `/swagger-ui.html` and loads from `/openapi.yaml`. The spec documents session-cookie auth (`JSESSIONID`) and all 7 endpoints across the Auth and Wallet tags.

### Test Conventions

- `*Test.java` — unit tests with Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`)
- `*IT.java` — integration tests with MockMvc (`@WebMvcTest` + `SecurityConfig`, `@MockitoBean` for services)
- `*Tests.java` — application context test (`@SpringBootTest`)
- Integration tests use `@ActiveProfiles("test")` (triggers H2 config from `application-test.yaml`)
- Controller tests validate redirects, flash attributes, and model attributes via MockMvc result matchers
- `WalletControllerIT` sets up session state via `MockHttpSession` with a `memberId` attribute
