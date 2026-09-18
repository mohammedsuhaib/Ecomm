# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Town Basket** — a quick-commerce platform for a single supermarket (live at
town-basket.com). A **Spring Modulith modular monolith** backend serves two
**Next.js** frontends: a customer-facing installable PWA (storefront) and a
store-staff dashboard (admin). See `ARCHITECTURE.md` for the full design and
`README.md` for the milestone roadmap (M1–M6).

> The README still describes a "pre-development" state and a Gradle backend.
> Both are stale: implementation is well underway and **the backend builds with
> Maven** (`mvnw`), not Gradle. Trust the code, not the README, on build tooling.

## Monorepo layout

This is a **hybrid monorepo with two independent build systems**:

- **`apps/api/`** — Spring Boot backend, built with **Maven** (`./mvnw`). Deliberately
  *not* part of the JS workspace.
- **pnpm workspace** (`pnpm-workspace.yaml`) — `apps/storefront`, `apps/admin`,
  `packages/*`. Driven by **pnpm** from the repo root.
- `infra/` — `docker-compose.yml` (local: postgres + api + both frontends) and
  `infra/deploy/` (prod: Caddy auto-TLS, `docker-compose.prod.yml`, nightly backups).
- `holding-site/` — a standalone static "coming soon" site; unrelated to the app.
- `brand/` — logos and icon assets.

## Commands

**Backend (`apps/api/`, run from that directory):**
```bash
./mvnw -B -ntp verify          # full build: compile + ALL tests, incl. Modulith boundary check + Testcontainers integration tests
./mvnw test -Dtest=OrderCheckoutIntegrationTest   # single test class
./mvnw test -Dtest=ModularityTests                # boundary verification only (no Docker needed)
./mvnw spring-boot:run         # run the API locally (needs a Postgres at DB_URL)
```
Integration tests need Docker running (Testcontainers spins up `postgres:16-alpine`).
`ModularityTests` is pure static analysis and runs without Docker.

**Frontends / packages (run from repo root):**
```bash
pnpm install --frozen-lockfile
pnpm -r --if-present run typecheck         # type-check every workspace package
pnpm --filter @town-basket/storefront dev  # storefront on :3000
pnpm --filter @town-basket/admin dev       # admin on :3001
pnpm --filter @town-basket/storefront build
pnpm --filter @town-basket/storefront lint # next lint (per-app)
pnpm pwa-gate                              # all three apps still installable? (needs their builds)
pnpm pwa-gate admin                        # ...or just one
```

**Full local stack (Postgres + API + both frontends, with seed data):**
```bash
docker compose -f infra/docker-compose.yml up --build
# storefront :3000   admin :3001   api :8080   postgres :5432
```

**CI** (`.github/workflows/ci.yml`) runs two jobs: `./mvnw verify` for the API
(uploads Modulith docs + surefire reports) and pnpm type-check + build for the
frontends, followed by the **PWA installability gate**
(`scripts/pwa-gate.mjs`) — it serves each standalone build and has Chrome, via
Lighthouse, confirm all three apps are still installable. Note the script pins
**Lighthouse 11.7.1**: Lighthouse 12 deleted the PWA category and the
`installable-manifest` audit, so a newer version would check nothing. See the
header comment in the script before touching that pin.

## Backend architecture (the part that needs reading multiple files)

The backend is a **Spring Modulith** app (`@Modulith`, `TownBasketApplication.java`)
under `com.townbasket`. Each module is **one Java package**: `identity`, `catalog`,
`inventory`, `cart`, `orders`, `payments`, `serviceability`, `notifications`, and
the OPEN `shared` kernel.

**Module structure convention** — for every module package:
- The package root holds the module's **public API**: `*Controller`, DTOs,
  service interfaces, a `*ModuleConfiguration`, and a `package-info.java`
  annotated `@ApplicationModule(...)`.
- A `internal/` subpackage holds implementations, JPA `*Entity` classes,
  `*Repository` interfaces, and module-private config. **Other modules may not
  reference anything in another module's `internal/`.**
- `shared` is declared `@ApplicationModule(type = OPEN)` — it is the one module
  every other module is allowed to depend on (domain event types, `Money`,
  error model). It contains no business logic.

**Boundary enforcement** — `ModularityTests` calls `ApplicationModules.verify()`,
which **fails the build** if a module reaches into another module's internals or
introduces a dependency cycle. This runs in CI. Treat a boundary violation as a
real failure, not a warning: communicate cross-module only through published
APIs or domain events.

**Inter-module communication** — modules publish **domain events** (types in
`shared/events`) through the Spring Modulith **event publication registry**, a
**transactional outbox** persisted in Postgres (`republish-outstanding-events-on-restart`
is on). E.g. an order state transition emits an event consumed by `inventory`,
`payments`, and `notifications`. Prefer this over direct service calls between modules.

**Persistence — schema per module:**
- Each module owns its **own Postgres schema** and its **own Flyway migration
  folder** under `src/main/resources/db/migration/<module>/`.
- Migrations use **per-module version bands**: shared `V1.x`, identity `V2.x`,
  catalog `V3.x`, inventory `V4.x`, cart `V5.x`, orders `V6.x`, payments `V7.x`,
  serviceability `V8.x`, notifications `V9.x`. When adding a migration, stay
  inside the module's band and use the next number (`flyway.out-of-order=true`
  is enabled precisely so independent module bands don't collide).
- Flyway tracks history in a dedicated `flyway` schema. JPA is `ddl-auto: validate`
  — **the schema is owned by migrations, never by Hibernate**. Add a Flyway
  migration for any schema change; don't rely on entity changes to alter the DB.

**Auth & security** (`SecurityConfig.java` + the `identity` module):
- The app issues its **own HS256 JWTs** (jjwt). Auth is stateless: a custom
  `JwtAuthenticationFilter` validates the `Bearer` token (resource-server style).
  `SecurityConfig` disables httpBasic/formLogin, and Boot's default
  `UserDetailsServiceAutoConfiguration` is excluded in `application.yml`.
- Customer login is **phone-OTP via Firebase**. When
  `townbasket.identity.firebase.project-id` is **unset** (the default, incl.
  local/docker), a **FAKE verifier** is active that accepts only `dev:<10-digit-phone>`
  tokens. Setting the project-id activates the real Google-signed-token verifier.
  Never give that property an empty-string default — empty counts as "present".
- Access-token TTL 15m, refresh 30d. Auth abuse limits are **two layers**, both
  in-memory fixed windows (no Redis): `RateLimitFilter` per **client IP** across
  `/auth/*` (default 60/60s — sized for a crowd behind one NAT/CGNAT address,
  not for one person), and `identity.internal.LoginAttemptLimiter` per **staff
  account**, counting only *failed* passwords and cleared by a success (default
  10 / 5 min) — that second layer is what bounds password guessing, so don't
  re-tighten the IP budget to do it. Customer login needs no per-credential
  twin: Firebase verifies the OTP, and `/auth/phone/verify` only consumes the
  resulting signed token.
- **A refresh token is the record of "signed in somewhere"**, and one thing
  outside the identity module reads it: `WebPushNotificationChannel` asks
  `AuthService#hasActiveSession` before pushing, so nothing is sent to a person
  who has signed out, expired, or been revoked. A browser push subscription
  outlives the session that made it and a signed-out phone may never run our
  code again, so this check — not a client-side unsubscribe — is what keeps a
  handed-on rider phone from announcing the previous rider's deliveries. Leave
  the subscription row alone; it goes quiet by itself.

## Frontend architecture

Both apps are **Next.js 14 App Router + TypeScript**. Storefront **and admin** are
installable **PWAs** via Serwist (`next.config.js` compiles `app/sw.ts` →
`public/sw.js`; disabled in dev). The rider app (`apps/delivery`) is installable
too, from a manifest plus a hand-written push-only `public/sw.js`. Production
images use `output: 'standalone'`.

- **The two service workers take opposite postures, on purpose.** The storefront
  caches the catalogue (stale-while-revalidate for the category nav, network-first
  for products and the store row) because a shopper seeing a slightly stale price
  is recoverable. **`apps/admin/app/sw.ts` caches the shell and nothing else** —
  no API response is ever cached, because staff *act* on the queue, and a cached
  order could have someone pick a cancelled order or a rider take the wrong
  parcel. That is affordable because admin's HTML carries no live data (the page
  is a client-rendered shell), so document + JS + CSS are static build output.
  Two consequences worth knowing before editing that file: it deliberately does
  **not** spread Serwist's `defaultCache` (its last rule is a NetworkFirst
  *cache* matching every cross-origin request — and the API is cross-origin), and
  it deliberately matches **nothing** on the API, so the worker stays out of the
  order queue's SSE stream (`/admin/orders/stream`).

- **Live rider location** is a *customer* field, `OrderDto.riderLocation`, and
  its gate lives in exactly one place: `OrderServiceImpl#riderLocationFor`. It is
  set only by `getOrderByToken` (the single-order tracking read), only while
  OUT_FOR_DELIVERY, only for the assigned rider, only while the fix is under
  3 minutes old; every list read passes null, so order history stays one query.
  It rides the order page's existing ~6 s poll **on purpose, not the SSE
  stream**: domain events go through the persisted outbox, and a GPS ping every
  ~8 s per rider is not an event worth persisting. The rider app
  (`apps/delivery/app/components/LocationSharing.tsx`) reports
  `PUT /delivery/location` while its queue is non-empty and `DELETE`s it when
  the queue empties, on Stop (remembered per phone), and on sign-out — before
  the token is dropped, or the delete could not be authorised. Storage is one
  row per rider in `orders.agent_locations`, overwritten each ping: current
  position only, never a track log. The **"About N minutes away"** on the card
  is a client-side estimate in `apps/storefront/app/lib/geo.ts`
  (`estimateMinutesAway`: crow-flies × 1.3 road factor at 18 km/h, rounded up,
  min 1) — deliberately not a directions API, which would be a billable
  server call per poll per open page and would still need this fallback when
  no Maps key is set. Tune the two constants there, not the copy.

- **The install ask** is `components/InstallPrompt.tsx` on top of `lib/install.ts`,
  in **both** the storefront and admin. Two mechanisms, not one: Chromium hands
  us a deferred `beforeinstallprompt` to replay from our own button, while iOS
  Safari fires nothing and can only be shown the Share-sheet steps.
  `lib/install.ts` registers its capture listener at **module scope** — the event
  fires once and is never replayed, so a listener added in an effect can miss it
  outright. Both remember a dismissal for good; they differ only in *when* they
  ask. The storefront waits for a second visit and stands down while the location
  gate is up (`useLocationGate().blocking`); admin asks any **signed-in** staffer
  straight away, because getting through the login form is already a stronger
  signal than any visit count, and it keeps the ask off the public login screen.
  The two `lib/install.ts` files are near-copies on purpose — same reason the API
  clients are per-app — so fix browser quirks in both.

- The REST contract is served under **`/api/v1`**. The frontends resolve it via
  `NEXT_PUBLIC_API_BASE_URL` (browser) / `INTERNAL_API_BASE_URL` (SSR inside Docker).
- **API access is a hand-written typed fetch client per app** (e.g.
  `apps/storefront/app/lib/api.ts` + `app/lib/types.ts`, `app/lib/auth.ts`).
  A generated client (springdoc-openapi → `openapi-typescript`, regenerated in
  CI to catch contract drift) is planned but does not exist yet — the old
  `packages/api-client` placeholder was deleted rather than left rotting. When
  touching API types, update each app's hand-written client.

## Conventions worth knowing

- Java package root: `com.townbasket`. Keep new backend code inside an existing
  module package and respect the API-vs-`internal` split.
- New integration tests extend `AbstractIntegrationTest` (shared singleton
  Testcontainers Postgres — see its Javadoc for why the container is static and
  never stopped per-class). `ModularityTests` deliberately does **not** extend it.
- Config is env-var driven so one image runs both locally and in prod
  (`DB_URL`, `JWT_SECRET`, `APP_CORS_ALLOWED_ORIGINS`, `FIREBASE_PROJECT_ID`, …).
  The dev defaults in `application.yml`/compose (JWT secret, Maps key) are clearly
  marked dev-only — override them in real deployments.