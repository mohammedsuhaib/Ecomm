# Town Basket — Architecture Document

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
   DigitalOcean Droplet (Bangalore region) — Docker Compose
        ┌──────────────────────────────────────────────┐
        │   Caddy — reverse proxy + auto-TLS            │
        │  shop.town-basket.com │ admin.… │ api.…       │
        └──────┬───────────────┬───────────────┬───────┘
               ▼               ▼               ▼
        ┌────────────┐  ┌────────────┐  ┌────────────────────┐
        │ Storefront │  │   Admin    │  │  Spring Boot        │
        │  Next.js   │  │  Next.js   │  │  Modular Monolith   │
        └────────────┘  └────────────┘  │ identity│catalog│…  │
                                        │ orders│payments│…   │
                                        └─────────┬──────────┘
                                                  ▼
                              ┌──────────────────────────┐
                              │   PostgreSQL              │
                              │   schema per module       │
                              │   + Modulith event registry│
                              └─────────────┬────────────┘
                                            ▼
                         DO Spaces — nightly DB backups + product images

All containers on one DigitalOcean droplet owned by the client; Postgres
either containerized or on DO Managed Database (recommended for durability).
Frontends call the API via the generated TS client (HTTPS/REST).
External: Paytm Payment Gateway (UPI payments) · Firebase Auth (phone OTP)
          Web Push/VAPID (notifications) · DO Spaces (product images)
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
  per-store availability flags. Each variant carries a **selling price**
  and a **cost price**, both maintained manually by store staff (no
  automated cost averaging); cost price feeds gross-profit reporting.
  Catalogue is **fixed-price packaged goods only** (dry groceries + dairy;
  no loose/weight-priced items), so variants are discrete pack sizes —
  no weigh-at-packing price adjustment.
- Search: Postgres full-text (`tsvector` + trigram for typo tolerance).
  Sufficient for a supermarket-sized catalog (~5–20k SKUs); a search
  service is a later extraction if ever needed.
- Read-heavy, and cached at three layers, none of them a CDN (there is no
  CDN, and none is planned — see §7):
  1. **HTTP cache headers.** The anonymous read endpoints (`/categories`,
     `/products*`, `/store`, `/serviceability/check`) send a short
     `public, max-age=…, stale-while-revalidate=…` plus a weak `ETag` for
     conditional GETs; everything else sends `no-store`. Set by
     `PublicReadCacheHeaderWriter`, which replaces Spring Security's own
     blanket-`no-store` writer — until it existed, *every* response,
     catalogue included, forbade caching outright.
  2. **Next.js fetch cache.** Catalogue fetches revalidate every 60s, so a
     burst of visitors costs one API call. Note this is the Data Cache, not
     full-page ISR: the root layout reads the locale cookie, so the pages
     themselves render per request even though they declare
     `revalidate = 60`.
  3. **Caffeine, in-process.** The active-store row and the category nav
     only, both evicted by the admin writes that change them. No Redis at
     this scale: one JVM, so a cache server would add a process to operate
     and a network hop in place of a map lookup.
- **Product images:** one photo per product, held as a URL on
  `products.image_url` rather than a table — the storefront renders a single
  thumbnail, so a gallery would be schema nobody displays. Staff upload a
  JPEG/PNG in the admin catalogue; the API re-encodes it (scaled to 1000px,
  EXIF stripped, format verified from the bytes — an SVG served from our own
  origin would be stored XSS) and stores it in DO Spaces, saving the public
  URL. The object is deleted when the product is deleted or re-imaged. Upload
  is optional: unconfigured, staff paste a URL instead, which is also how the
  seeded catalogue points at images bundled with the storefront.
- Owns: `categories`, `products`, `product_variants`.

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
  (plus `OUT_FOR_DELIVERY → DELIVERY_FAILED` with a mandatory reason when the rider cannot complete an attempt; from there `→ OUT_FOR_DELIVERY` re-dispatches with the same stock reservation and OTP, or `→ CANCELLED` releases stock once the goods are back on the shelf)
     └────────┴──────────┴──→ CANCELLED (with reason + stock release)
  ```

- Checkout is **idempotent**: the client sends an idempotency key;
  retries (flaky mobile networks) cannot double-order.
- Every order lands in the queue as **PLACED**, whatever the payment
  method; **staff confirm it** (`PLACED → CONFIRMED`) from the admin queue
  once they have looked at it. Payment state is tracked separately on the
  order (`paymentStatus`), so an unpaid or a prepaid order is equally
  visible to staff before they accept it.
- Each transition emits an event (`OrderPlaced`, `OrderConfirmed`, …)
  consumed by `inventory`, `payments`, `notifications`.
- **Delivery OTP (all orders, UPI and COD):** a one-time delivery code is
  generated per order and shown in the customer's app; the
  `→ DELIVERED` transition requires that code to be verified (entered by
  staff in the admin queue, or by the rider in the Phase-2 delivery role).
  Serves as proof of delivery and a COD fraud safeguard.
- Price snapshot stored on the order (catalog price changes never
  mutate history). When the analytics add-on is engaged, each order
  line also snapshots the **cost price at time of sale** (COGS), so
  gross-profit reporting stays accurate even as costs change later.
- Owns: `orders`, `order_items`, `order_events` (audit trail).

### 3.6 `payments`
- Two payment methods, chosen by the customer at checkout: **online UPI**
  (Paytm PG) and **Pay on Delivery (`COD`)** — settled at the door in cash or
  by UPI, so the code keeps the `COD` name while every customer- and
  staff-facing label says "Pay on Delivery".
- `PaymentProvider` port with implementations: `PaytmProvider` (UPI),
  `CodProvider`, and `FakeProvider` (tests/local/M3 demo). The port keeps
  methods independently swappable/addable.
- **Online (UPI):** Paytm PG flow — create a payment order → customer
  completes UPI in their payment app → **webhook/callback**
  (checksum-verified, idempotent) marks the payment PAID → emits
  `PaymentSucceeded` / `PaymentFailed`. The server verifies transaction
  status with Paytm before recording it (never off a client-side callback
  alone). Payment success does not confirm the order by itself — staff
  still confirm it from the queue (§3.5). Unpaid online orders auto-cancel
  after a timeout, releasing reserved stock.
- **COD:** no prepayment; the order is placed with payment `COD_PENDING`
  and recorded as collected when staff mark the order delivered. No
  auto-cancel-on-non-payment; cancellation before dispatch simply releases
  stock with no refund needed.
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
  `NotificationChannel` port.
- **Core (included):** **SSE** endpoint for the live order-tracking page
  (customer) and the admin order queue (new-order alerts). No external
  messaging — tracking works while the app/page is open.
- **Add-ons (priced, Annexure C):** Web Push (VAPID) for updates when the
  app is closed, plus Email / SMS / WhatsApp channels. Because every
  channel is a `NotificationChannel` implementation behind the same
  event consumer, each is an additive plug-in, not a rework.
- Delivery is best-effort and retried; a notification failure never
  blocks an order transition.
- Owns: `notification_log` (core); `push_subscriptions` added with the
  Web Push add-on.

### 3.9 `shared`
- Domain event types, money/quantity value types, error model. No
  business logic; only this module may be depended on by all others.

---

## 4. Frontend

### 4.1 Customer storefront — `apps/storefront` (Next.js, TypeScript)
- **PWA:** Workbox (via Serwist) service worker — precached app shell,
  stale-while-revalidate for catalog, offline fallback page, install
  prompt. Lighthouse PWA-installable is a CI gate (`scripts/pwa-gate.mjs`,
  which covers all three frontends and is pinned to Lighthouse 11.7.1 — 12
  removed the PWA category and with it the only installability audit).
  (Web-push subscription wiring lands with the Web Push add-on.)
- **Live rider location:** while an order is OUT_FOR_DELIVERY the tracking
  page shows where the rider is — distance to the address and how old the
  fix is, plus a map with rider and home pins when a Maps key is configured.
  The rider app reports `PUT /delivery/location` every ~8 s while it has
  deliveries in the queue and `DELETE`s it when the queue empties, on Stop,
  or on sign-out. The API keeps ONE current position per rider
  (`orders.agent_locations` — no history, by design) and includes it on the
  customer's single-order tracking read **only** while OUT_FOR_DELIVERY,
  only for the assigned rider, and only while the fix is under 3 minutes
  old — the same gate, in the same place, as the delivery OTP. It rides the
  page's existing status poll rather than the SSE stream: domain events go
  through the persisted outbox, and a GPS ping every few seconds per rider
  is not an event worth persisting, while a ~6 s poll is honest for a 5 km
  delivery. It is never on the admin or delivery surfaces, nor on the
  customer's order history list.
- **SSR** for catalog/product pages: fast first paint on mid-range
  Android over 4G, and indexable for SEO.
- Flows: location gate (5 km check) → browse/search → cart → address →
  checkout (UPI via Paytm PG, or Pay on Delivery) → live tracking (SSE)
  → history → reorder.
- Talks only to the backend's public REST API via the **generated
  TypeScript client** (see §6) — the frontend never knows internal
  module topology.

### 4.2 Admin dashboard — `apps/admin` (Next.js, TypeScript)
- Order queue with live updates (SSE) and one-tap status transitions;
  catalog CRUD; bulk inventory corrections; store config (radius,
  hours); basic daily sales/orders dashboard.
- Separate app from the storefront: different audience, auth, and
  release cadence — independently deployable.
- **PWA:** installable so staff run it from the taskbar or home screen
  without browser chrome, which is dead space on a screen showing a live
  queue all day. Serwist worker, but the **opposite caching posture to
  the storefront's: the app shell only, never an API response.** Staff act
  on what the dashboard shows, so a cached order queue could mean picking
  a cancelled order or handing a parcel to the wrong rider; offline it
  says so instead. Safe to cache the shell because the HTML carries no
  live data — it is client-rendered. Nothing matches the API, which also
  keeps the worker out of the order queue's SSE stream, and leaves no
  customer data in Cache Storage on a shared shop machine. Icon is the
  brand shopfront tile, kept distinct from the storefront's basket and
  the rider app's blue pin. Installability is a CI gate (§4.1).

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
│   ├── docker-compose.yml      # local: Postgres + api + apps + seed data
│   └── deploy/                 # prod: docker-compose.prod.yml, Caddyfile,
│       │                       #       backup + provisioning scripts
│       └── ...                 # (DigitalOcean droplet)
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

**Availability** *(single-server, cost-optimised for ~100 orders/day)*
- One DigitalOcean droplet running all containers under Docker Compose
  with restart policies; deploys via pull-and-restart (brief blip, not
  multi-instance zero-downtime — acceptable at this scale).
- **Durability is the priority, not failover:** automated **nightly
  PostgreSQL backups to DO Spaces** (off-server, retained, with a tested
  restore runbook). Postgres on **DO Managed Database** is recommended so
  backups/patching/PITR are handled by the provider.
- Conscious trade-off: a single droplet has **no automatic failover** —
  a host/provider outage means downtime until restart/restore (minutes
  to a couple of hours, rare). Acceptable for this business; data is
  protected by backups regardless.
- Graceful degradation: catalog browsing and cart remain available if
  payments is down (checkout shows a clear retry state); web-push
  retries; SSE clients auto-reconnect.

**Scalability (the dial, when needed)**
- Vertical first → resize the droplet (more CPU/RAM); trivial on DO. Six
  containers share that box, so each has a `mem_limit` in
  `docker-compose.prod.yml` and the API's heap is derived from its limit
  (`MaxRAMPercentage`): after a resize, raise the limits to use the new RAM —
  otherwise the box grows and nothing takes the extra. Unbounded, the ceiling
  is not throughput but an OOM-killed container, which reads like a
  performance problem and is a budgeting one.
- Read load → cache headers + Postgres tuning; add Redis if measured.
- Then horizontal/managed → move to a managed/HA setup (managed DB +
  multiple app instances behind a load balancer). This is **not** a
  deployment-only change. The app is containerised but it is not stateless,
  and a second instance breaks four things that work today precisely
  because there is one JVM:
  - `notifications.internal.SseRegistry` holds live `SseEmitter`s in a
    `ConcurrentHashMap`. An order event only reaches the instance that
    happens to hold that customer's connection, so behind a load balancer
    live tracking silently stops updating for most customers. Needs a
    shared pub/sub fan-out (Redis, or Postgres `LISTEN/NOTIFY`).
  - `RateLimitFilter` counts per IP in a local `ConcurrentHashMap`, and
    `identity.internal.LoginAttemptLimiter` counts failed staff logins per
    account the same way, so N instances mean N times both configured
    limits. Both need a shared counter.
  - The Caffeine caches (§3.2) are per JVM, so one instance's eviction
    leaves the others serving the old store row until their TTL lapses —
    a staff closure would apply on some instances and not others. Needs a
    shared cache or an eviction broadcast.
  - `republish-outstanding-events-on-restart` re-reads the outbox at
    startup. The registry is in Postgres and shared, so every instance
    that restarts would republish the same outstanding events. Needs the
    completion claim to be exclusive.
  None of this is worth building now — it is work for the day the traffic
  arrives, and listing it is how that day does not start with a surprise.
- Module extraction → Modulith event externalization + lift the module's
  schema into its own database. Contracts don't change.

**Security**
- Short-lived JWTs + rotating refresh tokens; role-based authorization
  per endpoint; admin app behind staff roles.
- Paytm PG webhook/checksum verification; idempotent webhook handling.
- Secrets in environment files with restricted permissions (never in the
  repo); firewall (only 80/443 + restricted SSH); auto-TLS via Caddy.
- Auth abuse limits are **two layers**, because one IP is not one person:
  many customers share a public address (shop WiFi, carrier CGNAT), so the
  per-IP window on `/auth/*` is sized for a crowd signing in together, and
  password guessing is bounded **per staff account** by a failed-attempt
  throttle that a correct password clears. Tightening the per-IP number to
  guard passwords instead would just answer 429 to real customers.

**Observability**
- Structured JSON logs (shipped/retained off-server); Spring Boot Actuator
  health/metrics; request tracing with correlation IDs from the frontend.
- Uptime + disk/memory/CPU monitoring with alerts; alerts on 5xx rate,
  failed event publications (outbox backlog), payment-webhook failures,
  and backup success/failure.

**Failure modes considered**
| Failure | Behavior |
|---|---|
| Paytm PG down | UPI checkout shows a clear retry message; **Pay on Delivery remains available as a fallback**; browsing/cart unaffected; unpaid online orders auto-cancel + release stock |
| SMS/OTP provider down | Existing sessions unaffected (our JWTs); staff email login unaffected |
| A container crashes | Docker restart policy brings it back automatically |
| Droplet/host outage | Downtime until restart; restore from nightly backup if needed (no auto-failover — accepted trade-off) |
| Double-submit checkout | Idempotency key returns the original order |
| Webhook replay/duplicate | Idempotent handler keyed on Paytm PG order/txn id |
| Stock oversell race | Atomic conditional UPDATE on reservation; no oversell |

---

## 8. Environments & CI/CD

- **Local:** `docker-compose up` → Postgres + API + both apps + seed
  data (catalog, one store, test users). Testcontainers for integration
  tests — the test DB is real Postgres.
- **CI (GitHub Actions):** build + unit/integration tests +
  `ModularityTests` (Modulith boundary verification) + OpenAPI client
  regeneration + frontend type-check/build + Lighthouse PWA check.
- **CD:** merge to `main` → build images → push to a registry → the
  droplet pulls and restarts via `docker-compose.prod.yml` (Caddy fronts
  the three subdomains with auto-TLS). Optional staging on a small droplet.
- **Cost estimate (production):** one DigitalOcean droplet
  (2 vCPU / 4 GB, Bangalore) ≈ ₹2,000/month; + DO Spaces for backups/
  images (~₹150) and, recommended, DO Managed Postgres (~₹1,200) →
  **≈ ₹2,000–3,300/month total** on the client's DigitalOcean account.

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
4. **M4 — Identity:** phone OTP (Firebase), JWTs, one saved address,
   basic order history; staff logins + roles.
5. **M5 — Payments:** Paytm PG UPI + webhook flow live, auto-cancel
   timeout.
6. **M6 — Production:** DigitalOcean droplet provisioning + Docker Compose
   deploy (Caddy + subdomains + auto-TLS), nightly DB backups to Spaces,
   monitoring/alerts, admin inventory/catalog management polish.

This is the **Core Package** (the commercial fixed-price scope). Order
tracking and admin alerts use in-app SSE; no external messaging in core.

**Add-ons — designed-for, built only when ordered (Annexure C of the
contract):** web push / email / SMS / WhatsApp notifications, multiple
addresses, one-tap reorder, offers/coupons, loyalty/wallet, delivery-slot
scheduling, ratings & reviews, analytics dashboard,
multi-store, POS/ERP sync, native app. Each maps to an existing module or
provider port, so it is additive — no rebuild of delivered functionality.

**Engaged add-ons (Phase 2):**
- *Delivery management (role-based):* add a `DELIVERY` role in
  `identity` and a small `delivery` context (assignment, delivery status,
  proof of delivery) with mobile-friendly pages in the existing PWA. The
  `OUT_FOR_DELIVERY → DELIVERED` transitions move from staff to the
  assigned delivery person. Originally scoped with no GPS; live rider
  location was added later at the client's request — see §4.1 for the
  design and the gate on who may see it.
- *Sales & analytics dashboard incl. gross-profit %:* a read-model over
  order data. Uses the manually-maintained **cost price** on each product
  (`catalog`) and the per-line **COGS snapshot** in `orders` (above).
  Profit is reported as gross margin (selling price − cost price); no
  weighted-average or FIFO costing — the current cost set by staff is
  used and snapshotted at the time of each sale.

---

## 10. Key decisions record

| # | Decision | Why | Revisit when |
|---|---|---|---|
| 1 | Modular monolith (Spring Modulith), not microservices | 100 orders/day; boundaries give the coupling benefits without the ops tax | A module needs independent scaling/team |
| 2 | Postgres outbox (Modulith registry), no broker | Transactional with business data; zero extra infra | Event volume or external consumers demand SQS/SNS |
| 3 | Next.js for both frontends | SSR speed on low-end mobiles, PWA tooling, image pipeline | — |
| 4 | Generated TS client from OpenAPI | Closes the Java↔TS type gap mechanically | — |
| 5 | Firebase Auth for phone OTP, own JWTs | Don't build SMS/OTP infra; vendor swappable | Cost/SMS deliverability issues |
| 6 | Paytm PG (UPI) + Pay on Delivery behind `PaymentProvider` | Customer picks method at checkout; COD also a fallback if UPI is down | New vendor/method requested |
| 7 | Single DigitalOcean droplet (Docker Compose), not AWS multi-AZ | Cost is the priority at ~100 orders/day; ~₹2–3k/mo vs ~₹9–16k; HA overkill here | Sustained growth or store becomes mission-critical → managed/HA |
| 7a | Durability via nightly off-site backups (+ optional managed Postgres); accept no auto-failover | Protect data (non-negotiable) while trading away availability (tolerable at this scale) | Downtime starts costing real revenue |
| 7b | Caddy reverse proxy + auto-TLS; DO Spaces for images/backups | Simple, free TLS, S3-compatible object storage | — |
| 8 | Postgres FTS for search | Catalog is small; one less system | Search quality complaints at scale |
| 9 | `store_id` everywhere, one store operated | Multi-store retrofit is brutal; modeling it now is cheap | — |
