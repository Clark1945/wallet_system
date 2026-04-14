# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run the application
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

## Architecture Overview

**Stack:** Spring Boot 4.0.5 · Java 17 · Spring Data JPA · PostgreSQL · Thymeleaf · Lombok

**Base package:** `org.side_project.wallet_system`

**Module layout** under `src/main/java/.../wallet_system/`:
- `auth/` — `AuthController`, `AuthService`, `Member` (JPA entity), `MemberRepository`, `ResponseDto`
- `wallet/` — `WalletController`, `WalletService`, `Wallet` (JPA entity), `WalletRepository`
- `payment/` — `Transaction` (JPA entity), `TransactionRepository`, `TransactionType` (enum)
- `config/` — `WebConfig` (BCrypt bean, `AcceptHeaderLocaleResolver`, `LoginInterceptor` registration)
- `interceptor/` — `LoginInterceptor` (session guard; redirects to `/login` when `memberId` is absent)

This is a **server-rendered MVC app**, not a REST API. All controllers are `@Controller` (not `@RestController`) and return Thymeleaf view names. Flash attributes carry success/error messages between redirects.

### Authentication & Session

Session-based auth using `HttpSession`. On successful login, `memberId` (UUID) is stored in the session. `LoginInterceptor` protects all routes except `/login` and `/register`. No Spring Security is used — only `spring-security-crypto` for `BCryptPasswordEncoder`.

### Wallet & Transaction Flow

- Each `Member` has exactly one `Wallet` (OneToOne). The wallet is created automatically during registration.
- `Wallet.walletCode` is a 12-char alphanumeric string generated via `SecureRandom` and used for peer-to-peer transfers.
- `WalletService` methods (`deposit`, `withdraw`, `transfer`) are `@Transactional`. They throw `IllegalArgumentException` with i18n message keys on invalid input (negative amounts, insufficient balance, self-transfer, unknown wallet code).
- `Transaction` records every operation, with nullable `fromWalletId`/`toWalletId` depending on `TransactionType` (DEPOSIT / WITHDRAW / TRANSFER).

### Internationalization

`AcceptHeaderLocaleResolver` with supported locales: `en`, `ja`, `zh-TW` (default: `en`). Message bundles are in `src/main/resources/messages*.properties`. All user-facing strings — including error messages thrown in services — use message keys resolved via `MessageSource`.

### Database

- **Runtime:** PostgreSQL (`localhost:5432/wallet_db`, user: `postgres`)
- **Tests:** H2 in-memory with `ddl-auto: create-drop`; SQL init is disabled for tests (Hibernate recreates schema)
- **DDL:** `src/main/resources/schema.sql` runs on startup (`spring.sql.init.mode: always`). `ddl-auto: none` — schema is managed manually via this file and Flyway-style scripts.
- `script.sql` at the repo root is a standalone PostgreSQL DDL script for manual setup.

### OpenAPI / Swagger

Spec-first approach: `src/main/resources/static/openapi.yaml` is the source of truth. Swagger UI is served at `/swagger-ui.html` and loads from `/openapi.yaml`. The spec documents session-cookie auth (`JSESSIONID`) and all 7 endpoints across the Auth and Wallet tags.

### Test Conventions

- `*Test.java` — unit tests with Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`)
- `*IT.java` — integration tests with MockMvc (`@WebMvcTest`, `@MockitoBean`)
- `*Tests.java` — application context test (`@SpringBootTest`)
- Integration tests use `@ActiveProfiles("test")` (triggers H2 config from `application-test.yaml`)
- Controller tests validate redirects, flash attributes, and model attributes via MockMvc result matchers
