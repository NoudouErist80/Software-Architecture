# VIBE — Testing & Coverage

## 1. Testing strategy (test pyramid)

| Level | Tooling | Scope | Example |
|-------|---------|-------|---------|
| **Unit** | JUnit 5 + Mockito + AssertJ | A single class with all collaborators mocked. No DB/Redis/Kafka. | `AuthServiceTest`, `ContactServiceTest`, `TokenCacheServiceTest`, `JwtUtilTest` |
| **Integration** | `@WebMvcTest` + MockMvc | The real Spring MVC stack (routing, JSON, validation) with the service layer mocked. | `AuthControllerTest` |
| **End‑to‑end** | shell + `curl` against the running stack | Full cross‑service flow through the API gateway. | `register → login → send message → +1 token` (see `*.sh` smoke scripts) |

Coverage is measured with **JaCoCo 0.8.11**. Lombok‑generated boilerplate is
excluded from coverage via `lombok.config` (`lombok.addLombokGeneratedAnnotation = true`),
and non‑logic classes (the `@SpringBootApplication` bootstrap, `@Configuration`,
security wiring, DTOs/entities, Kafka publishers) are excluded in the JaCoCo
plugin config — so coverage reflects real business logic.

## 2. How to run

```bash
cd VIBE-WebApp/VIBE_Backend

# all services that have tests, with a coverage summary:
./run-tests.sh

# a single service:
./run-tests.sh auth-service

# or directly with Maven (HTML report at target/site/jacoco/index.html):
cd auth-service && mvn test
```

> Build with **JDK 17** (`JAVA_HOME`). Lombok does not support JDK 25.

## 3. Results

### Summary — services covered so far

| Service | Tests | Coverage (instructions) | Levels |
|---------|------:|------------------------:|--------|
| **auth-service** | 54 | **85.6%** | unit + integration |
| **messaging-service** | 56 | **85.4%** | unit + controller (REST + WebSocket) |
| **Total** | **110** | **>80% on every covered module** | |

### messaging-service

```
MessagingServiceTest .. 32 tests   (unit — send/read/edit/delete, conversations,
                                     groups, admin/community, member management)
MessageControllerTest . 17 tests   (REST endpoints + STOMP @MessageMapping handlers)
PresenceServiceTest .... 7 tests   (Redis presence + /topic/presence broadcast)
──────────────────────────────────
TOTAL ................. 56 tests   — 0 failures

COVERAGE:  85.4% instructions
  PresenceService    93.3%
  MessagingService   90.2%
  MessageController   64.4%
```
HTML report: `messaging-service/target/site/jacoco/index.html`

### auth-service (reference module)

```
JwtUtilTest ............ 5 tests   (unit)
AuthServiceTest ....... 24 tests   (unit)
ContactServiceTest .... 15 tests   (unit)
TokenCacheServiceTest .. 5 tests   (unit)
AuthControllerTest ..... 5 tests   (integration / MockMvc)
──────────────────────────────────
TOTAL ................. 54 tests   — 0 failures

COVERAGE:  85.6% instructions   73.5% branches   (target: 80%)
  ContactService     96.1%
  AuthService        95.0%
  TokenCacheService  84.8%
```

HTML report: `auth-service/target/site/jacoco/index.html`
XML/CSV (for CI): `auth-service/target/site/jacoco/jacoco.{xml,csv}`

## 4. Sample test cases (what is verified)

**AuthService (unit):** successful registration issues JWTs + publishes the
`UserRegistered` event + caches the refresh token; duplicate email/username →
`409`; invalid phone → `400`; login success updates last‑login; wrong password
increments the failed‑attempts counter; suspended/locked accounts are rejected;
refresh‑token revocation; password‑reset OTP validation; phone‑OTP verification.

**ContactService (unit):** contact listing; search by exact phone, phone suffix,
and username; the searcher is always excluded from results; idempotent add with
custom display name; "cannot add yourself"; block/unblock; friend suggestions.

**TokenCacheService (unit):** Redis read/write/delete, and the transparent
**in‑memory fallback** when Redis throws (so auth keeps working without Redis).

**AuthController (integration):** `POST /register` → 201 + tokens; invalid body
→ 400 (bean validation); `POST /login` → 200; `GET /me`; `PATCH /me/avatar`.

## 5. Extending to the other services

The pattern is identical for every service:
1. Add the **JaCoCo plugin** (+ `<excludes>`) to the service `pom.xml`
   (copy from `auth-service/pom.xml`).
2. Write `*Test` classes under `src/test/java/...` — Mockito unit tests for the
   `service` package, `@WebMvcTest` for the `controller` package.
3. Run `./run-tests.sh <service>`.

Priority order by business value: `rewards-service` (token economy),
`messaging-service` (chat + AI), then `feed-service`, `notification-service`, `ai-service`.
