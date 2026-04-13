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
./mvnw test -Dtest=WalletSystemApplicationTests

# Run a specific test method
./mvnw test -Dtest=WalletSystemApplicationTests#methodName
```

No separate lint step is configured; the compiler plugin handles annotation processing via Lombok.

## Architecture Overview

**Stack:** Spring Boot 4.0.5 · Java 17 · Spring Data JPA · PostgreSQL · Lombok

**Base package:** `org.side_project.wallet_system`

**Module layout** under `src/main/java/.../wallet_system/`:
- `auth/` — the only implemented module; contains `AuthController`, `Member` (JPA entity), and `ResponseDto`
- `bank/`, `payment/`, `wallet/` — empty placeholders for future modules

### API Versioning

Spring Boot 4's built-in MVC API versioning is used. Controllers declare a version on `@RequestMapping`:

```java
@RequestMapping(value = "/auth", version = "1")
```

`application.yaml` controls the versioning strategy:
```yaml
spring.mvc.apiversion.use.path-segment: 0   # 0 = disabled (header/query-param strategy)
```

### Response Convention

All endpoints return `ResponseEntity<ResponseDto>` where `ResponseDto` is a Java record:

```java
record ResponseDto(String message, String traceId) {}
```

`traceId` is currently always `null`; it is reserved for distributed tracing.

### Database

PostgreSQL is the only configured datasource (runtime dependency). JPA/Hibernate settings (DDL, dialect) are not yet present in `application.yaml` — add them before enabling persistence.
