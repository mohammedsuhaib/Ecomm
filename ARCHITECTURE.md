# QuickMart — Architecture Document

Quick-commerce platform for a single supermarket delivering within a 5 km
radius. Customer-facing PWA + store-ops admin dashboard. No native apps,
no delivery-person app.

**Design targets:** ~100 orders/day (peak ~15/hour), high availability,
loose coupling between domains, 10–50x growth absorbed by configuration
and service extraction — not a rewrite.

---

## 1. Guiding principles

1. **Modular monolith, strict boundaries.** One deployable Spring Boot
   application split into Spring Modulith modules. Modules communicate
   only via published APIs and domain events — never by reaching into
   each other's internals. Modulith verifies this at test time; CI fails
   on boundary violations.
2. **Schema-per-module.** Each module owns a dedicated Postgres schema.
   No cross-schema joins, no shared tables. This is the "no shared
   database" rule, enforced from day one, so any module can be extracted
   into its own service (with its own database) later.
3. **Events over calls for workflows.** Order lifecycle, stock changes,
   and notifications flow through domain events persisted via the
   Spring Modulith event publication registry (transactional outbox on
   Postgres). Swapping the transport to SQS/SNS later is configuration
   (Modulith event externalization), not a redesign.
4. **Ports for everything external.** Payments, SMS/OTP, push
   notifications, and geocoding sit behind interfaces with at least two
   implementations (real + fake for tests). Vendors are swappable.
5. **Multi-store data model, single-store operation.** Every relevant
   table carries `store_id`; serviceability is configured per store.
   MVP runs one store.
6. **Earn complexity.** No Kafka, no Kubernetes, no Redis until a
   measured need appears. At this scale, fewer moving parts = higher
   availability.

---

## 2. System overview

```
                ┌──────────────────────────┐
                │   Customer PWA (Next.js)  │  Vercel (CDN/edge)
                └────────────┬─────────────┘
                ┌────────────┴─────────────┐
                │  Admin Dashboard (Next.js)│  Vercel
                └────────────┬─────────────┘
                             │ HTTPS (REST, generated TS client)
                             ▼
                ┌──────────────────────────┐
                │   ALB  (health checks)    │
                └────────────┬─────────────┘
                             ▼
        ┌─────────────────────────────────────────┐
        │   Spring Boot Modular Monolith (×2 tasks)│  ECS Fargate
        │                                          │  ap-south-1
        │  identity │ catalog │ inventory │ cart   │
        │  orders │ payments │ serviceability      │
        │  notifications │ shared                  │
        └────────────────────┬─────────────────────┘
                             ▼
                ┌──────────────────────────┐
                │  RDS Postgres (multi-AZ)  │  schema per module
                │  + Modulith event registry│  (transactional outbox)
                └──────────────────────────┘

External: Razorpay (payments) · Firebase Auth (phone OTP)
          Web Push/VAPID (notifications) · S3+CloudFront (product images)
```

---

## 3. Modules

### 3.1 `identity`
- Phone-OTP login delegated to Firebase Auth (managed SMS/OTP); backend
  verifies the Firebase ID token once and issues **our own JWTs**
  (access + refresh), so the auth vendor is swappable.
- Roles: `CUSTOMER`, `STORE_STAFF`, `ADMIN`. Staff/admin log in with
  email + password (no OTP dependency for operations).
- Owns: `users`, `addresses`, `refresh_tokens`.

### 3.2 `catalog`
- Categories, products, variants (e.g., 500 g / 1 kg), images, pricing,
  per-store availability flags.
- Search: Postgres full-text (`tsvector` + trigram for typo tolerance).
  Sufficient for a supermarket-sized catalog (~5–20k SKUs); a search
  service is a later extraction if ever needed.
- Read-heavy: responses carry HTTP cache headers; CDN and Next.js cache
  hot category/product pages. No Redis at this scale.
- Owns: `categories`, `products`, `product_variants`, `product_images`.

### 3.3 `inventory`
- Stock per `(store_id, variant_id)`: `on_hand`, `reserved`.
- Checkout **reserves** stock atomically (single UPDATE guard,
  `on_hand - reserved >= qty`); reservation is committed on order
  confirmation or released on cancellation/payment timeout.
- Emits `StockLow`, `StockChanged` events. Admin gets a fast bulk
  count-correction screen — daily reconciliation is a fact of grocery.
- This system is the source of truth; a POS/ERP sync adapter can be
  added later behind the existing mutation API without touching orders.
- Owns: `stock_levels`, `stock_movements` (audit ledger), `reservations`.

### 3.4 `cart`
- Server-side cart keyed to user (survives devices/reinstalls), with
  anonymous-cart merge on login. Validates price/stock at read time.
- Owns: `carts`, `cart_items`.

### 3.5 `orders`
- The order **state machine**, staff-driven from the admin queue:

  ```
  PLACED → CONFIRMED → PACKING → OUT_FOR_DELIVERY → DELIVERED
     └────────┴──────────┴──→ CANCELLED (with reason + stock release)
  ```

- Checkout is **idempotent**: the client sends an idempotency key;
  retries (flaky mobile networks) cannot double-order.
- Each transition emits an event (`OrderPlaced`, `OrderConfirmed`, …)
  consumed by `inventory`, `payments`, `notifications`.
- Price snapshot stored on the order (catalog price changes never
  mutate history).
- Owns: `orders`, `order_items`, `order_events` (audit trail).

### 3.6 `payments`
- Online payments only — Cash on Delivery is a deliberate product
  exclusion. Orders reach CONFIRMED only after payment succeeds.
- `PaymentProvider` port with two implementations:
  `RazorpayProvider`, `FakeProvider` (tests/local/M3 demo). Adding a
  COD or alternative provider later is a new implementation, not a
  redesign.
- Razorpay flow: create payment intent → client completes UPI/card →
  **webhook** (signature-verified, idempotent) confirms → emits
  `PaymentSucceeded` / `PaymentFailed`. Orders are never confirmed off
  a client-side callback alone.
- Unpaid orders auto-cancel after a timeout, releasing reserved stock.
- Owns: `payments`, `payment_webhook_log`.

### 3.7 `serviceability`
- Stores table with lat/lng and configurable `delivery_radius_m`
  (default 5,000) and operating hours.
- Distance check (Haversine; PostGIS if polygon zones are ever wanted)
  enforced at **address selection and again at checkout**.
- Geocoding behind a `GeocodingProvider` port (Google Maps / Ola Maps /
  Nominatim — swappable). MVP can lean on browser geolocation +
  pin-drop, minimizing geocoding cost.
- Owns: `stores`, `delivery_zones`.

### 3.8 `notifications`
- Consumes order/payment events; fans out to channels behind a
  `NotificationChannel` port: **Web Push (VAPID)** for status updates,
  **SSE** endpoint for the live order-tracking page and the admin order
  queue (new-order alerts). SMS/WhatsApp channels are later plug-ins.
- Delivery is best-effort and retried; a notification failure never
  blocks an order transition.
- Owns: `push_subscriptions`, `notification_log`.

### 3.9 `shared`
- Domain event types, money/quantity value types, error model. No
  business logic; only this module may be depended on by all others.

---

## 4. Frontend

### 4.1 Customer storefront — `apps/storefront` (Next.js, TypeScript)
- **PWA:** Workbox (via Serwist) service worker — precached app shell,
  stale-while-revalidate for catalog, offline fallback page, install
  prompt, web-push subscription. Lighthouse PWA-installable is a CI gate.
- **SSR** for catalog/product pages: fast first paint on mid-range
  Android over 4G, and indexable for SEO.
- Flows: location gate (5 km check) → browse/search → cart → address →
  checkout (Razorpay) → live tracking (SSE) → history → reorder.
- Talks only to the backend's public REST API via the **generated
  TypeScript client** (see §6) — the frontend never knows internal
  module topology.

### 4.2 Admin dashboard — `apps/admin` (Next.js, TypeScript)
- Order queue with live updates (SSE) and one-tap status transitions;
  catalog CRUD; bulk inventory corrections; store config (radius,
  hours); basic daily sales/orders dashboard.
- Separate app from the storefront: different audience, auth, and
  release cadence — independently deployable.

---

## 5. Repository layout

```
Ecomm/
├── apps/
│   ├── api/              # Spring Boot modular monolith (Gradle, Java 21)
│   │   └── src/main/java/com/quickmart/{identity,catalog,inventory,
│   │                                    cart,orders,payments,
│   │                                    serviceability,notifications,shared}
│   ├── storefront/       # Next.js customer PWA
│   └── admin/            # Next.js admin dashboard
├── packages/
│   └── api-client/       # TS client generated from OpenAPI (CI artifact)
├── infra/
│   ├── docker-compose.yml   # local: Postgres + api + apps + seed data
│   └── terraform/           # ECS, RDS, ALB, S3/CloudFront, secrets
├── .github/workflows/       # CI/CD (see §8)
└── ARCHITECTURE.md
```

---

## 6. API contract (the Java ↔ TypeScript bridge)

1. springdoc-openapi generates `openapi.json` from the Spring
   controllers (single source of truth).
2. CI regenerates `packages/api-client` (openapi-typescript +
   typed fetch wrapper) and fails if the frontend no longer compiles —
   **contract drift is caught at build time, not in production.**
3. Public API is versioned under `/api/v1`; breaking changes require a
   new version.

---

## 7. Cross-cutting concerns

**Availability**
- 2 Fargate tasks minimum across two AZs behind an ALB; rolling deploys
  with health checks → zero-downtime releases.
- RDS Postgres multi-AZ with automated failover, daily snapshots, PITR.
- Graceful degradation: catalog browsing and cart remain available if
  payments is down (checkout shows a clear retry state); web-push
  retries; SSE clients auto-reconnect.

**Scalability (the 10–50x dial)**
- Stateless API → raise Fargate task count.
- Read load → CDN/cache headers already in place; add Redis if measured.
- DB → bigger RDS tier, then read replicas.
- Module extraction → Modulith event externalization to SQS + lift the
  module's schema into its own database. Contracts don't change.

**Security**
- Short-lived JWTs + rotating refresh tokens; role-based authorization
  per endpoint; admin app behind staff roles.
- Razorpay webhook signature verification; idempotent webhook handling.
- Secrets in AWS Secrets Manager (never in the repo); TLS everywhere;
  rate limiting on OTP and checkout endpoints.

**Observability**
- Structured JSON logs → CloudWatch; Spring Boot Actuator health/metrics;
  request tracing with correlation IDs propagated from the frontend.
- Alarms: 5xx rate, p95 latency, DB CPU/connections, failed event
  publications (outbox backlog), payment-webhook failures.

**Failure modes considered**
| Failure | Behavior |
|---|---|
| Razorpay down | Checkout temporarily unavailable with clear retry messaging; browsing/cart unaffected; unpaid orders auto-cancel + release stock |
| SMS/OTP provider down | Existing sessions unaffected (our JWTs); staff email login unaffected |
| One API task dies | ALB routes to the healthy task; ECS replaces it |
| AZ outage | Second task in other AZ; RDS fails over |
| Double-submit checkout | Idempotency key returns the original order |
| Webhook replay/duplicate | Idempotent handler keyed on Razorpay event id |
| Stock oversell race | Atomic conditional UPDATE on reservation; no oversell |

---

## 8. Environments & CI/CD

- **Local:** `docker-compose up` → Postgres + API + both apps + seed
  data (catalog, one store, test users). Testcontainers for integration
  tests — the test DB is real Postgres.
- **CI (GitHub Actions):** build + unit/integration tests +
  `ModularityTests` (Modulith boundary verification) + OpenAPI client
  regeneration + frontend type-check/build + Lighthouse PWA check.
- **CD:** merge to `main` → build images → deploy to **staging** →
  smoke test → manual approve → **production** (rolling).
- **Cost estimate (production):** Fargate ×2 small tasks + ALB +
  RDS small multi-AZ + S3/CloudFront + Vercel ≈ **$80–150/month**.

---

## 9. MVP slice (build order)

A thin vertical slice first — one real order through every module
boundary validates the architecture better than any document.

1. **M1 — Skeleton:** monorepo, Modulith app with module boundaries +
   boundary tests, Postgres schemas + Flyway, docker-compose, CI green.
2. **M2 — Browse:** catalog + serviceability (5 km gate), storefront
   browse/search, seed data, PWA shell installable.
3. **M3 — Order:** cart, checkout with idempotency + stock
   reservation (payments via `FakeProvider` in test mode), order state
   machine, admin order queue with SSE, customer tracking page.
   **First end-to-end order.**
4. **M4 — Identity:** phone OTP (Firebase), JWTs, addresses, order
   history; staff logins + roles.
5. **M5 — Payments:** Razorpay intent + webhook flow live, auto-cancel
   timeout; web-push status notifications.
6. **M6 — Production:** Terraform, staging + prod on AWS, observability
   + alarms, admin inventory/catalog management polish.

Out of MVP scope (designed-for, not built): offers/coupons, loyalty,
delivery-slot scheduling, POS sync, multi-store operations, analytics
beyond basic dashboards.

---

## 10. Key decisions record

| # | Decision | Why | Revisit when |
|---|---|---|---|
| 1 | Modular monolith (Spring Modulith), not microservices | 100 orders/day; boundaries give the coupling benefits without the ops tax | A module needs independent scaling/team |
| 2 | Postgres outbox (Modulith registry), no broker | Transactional with business data; zero extra infra | Event volume or external consumers demand SQS/SNS |
| 3 | Next.js for both frontends | SSR speed on low-end mobiles, PWA tooling, image pipeline | — |
| 4 | Generated TS client from OpenAPI | Closes the Java↔TS type gap mechanically | — |
| 5 | Firebase Auth for phone OTP, own JWTs | Don't build SMS/OTP infra; vendor swappable | Cost/SMS deliverability issues |
| 6 | Razorpay only behind `PaymentProvider`; no COD | Online-only by product decision; port keeps COD/vendors addable later | Client requests COD or new vendor |
| 7 | ECS Fargate + RDS multi-AZ, no Kubernetes | HA without an ops full-timer | Multiple services + dedicated ops |
| 8 | Postgres FTS for search | Catalog is small; one less system | Search quality complaints at scale |
| 9 | `store_id` everywhere, one store operated | Multi-store retrofit is brutal; modeling it now is cheap | — |
