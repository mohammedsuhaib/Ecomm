# Town Basket → SaaS: conversion plan

**Status:** proposal, nothing implemented. This document exists to be argued
with before any code is written.

**Scope:** what it would take to turn Town Basket from one supermarket's
quick-commerce platform into a multi-tenant SaaS that many supermarkets sign up
for. It covers the tenancy model, every schema change module by module, how
tenant context is resolved and enforced, the per-tenant configuration
inventory, the frontend/deployment story, and a phased sequence.

---

## 0. Verdict

**Yes, and the groundwork already done makes it a retrofit rather than a
rewrite — but not a small one.**

ARCHITECTURE.md §1.5 and decision record row 9 committed to "`store_id`
everywhere, one store operated." That bet pays off here: `orders` and
`inventory` already carry `store_id` and already filter by it, and the
`serviceability.stores` table is already a multi-row table rather than a
singleton config blob. Three or four modules are genuinely close.

The bet was only half-taken, though. `catalog`, `cart`, `identity`,
`payments`, `notifications` and `tax` have no tenant dimension at all, and
three specific seams hardcode the single store:

| Seam | Location | Problem |
|---|---|---|
| `resolveActiveStoreId()` returns `1L` | `apps/api/src/main/java/com/townbasket/orders/internal/OrderServiceImpl.java:701-706` | Every order is written to store 1. The method's own comment anticipates this change. |
| `findFirstByActiveTrueOrderByIdAsc()` | `apps/api/src/main/java/com/townbasket/serviceability/internal/StoreRepository.java` + `ActiveStoreCache.java` | "The" active store, cached with `maximumSize 1` (`CacheConfig.java:75`). |
| `@RequestParam(defaultValue = "1") Long storeId` | `AdminInventoryController.java:36,49`; `AnalyticsController.java:31,38,46,54` | **The tenant id is supplied by the client.** |

That last row is the one to internalise. Today it is harmless — there is one
store, and every authenticated staff member belongs to it. The moment a second
tenant exists, `GET /api/v1/admin/analytics/summary?storeId=2` is a competitor's
revenue, and `POST /api/v1/admin/inventory/correct?storeId=2` is write access to
their stock. **Multi-tenancy cannot be added incrementally on top of those
parameters** — the parameters have to go before the second tenant is created,
not after. That constraint shapes the whole phasing below.

Rough sizing, for calibration rather than commitment: the backend is 226 Java
files / ~13.9k lines with 16 controllers and 37 test files. P0 below (the
isolation foundation) is the substantial piece; the rest is incremental and
individually shippable.

---

## 1. Where the codebase stands today

| Module | Tenant column today | Gap |
|---|---|---|
| `serviceability` | `stores` is a real multi-row table with `active` flag | Anchor is there; the *lookup* is singleton |
| `inventory` | `store_id` on `stock_levels`, `UNIQUE (store_id, variant_id)` | Good. `reservations` / `stock_movements` have none (reachable via order/variant, but not directly filterable) |
| `orders` | `store_id NOT NULL` on `orders` | Good. `order_items` / `order_events` inherit via `order_id`. `invoice_series` is keyed on `fy` alone |
| `analytics` | Reads `WHERE store_id = :storeId` throughout | Correct shape, wrong source — the id comes from the query string |
| `catalog` | None | `products.slug` and `categories.slug` are **globally** `UNIQUE` |
| `cart` | None | Carts are anonymous UUIDs with no tenant |
| `identity` | None | `users.phone` and `users.email` are **globally** `UNIQUE` |
| `payments` | None | Reachable via `order_id` only |
| `notifications` | None | `notification_log`, `push_subscriptions` reachable via `order_id` only |
| `tax` | None | HSN/GST rates are arguably genuinely global — see §6 |
| `delivery` | None | Rider queue is not store-filtered |

Two structural facts worth stating up front, because they constrain the design:

1. **"Schema" is already taken.** This codebase uses one Postgres schema *per
   module* (`catalog`, `orders`, …), with per-module Flyway version bands. Any
   design that also wants a schema per tenant multiplies schemas by tenants and
   makes the Flyway story untenable. See §2.
2. **Not all data access goes through JPA.** `AnalyticsServiceImpl` and
   `AdminInventoryServiceImpl` build raw SQL against `NamedParameterJdbcTemplate`.
   Any tenant filter implemented purely as a Hibernate feature will be silently
   bypassed by exactly the queries that read the most sensitive data. This is
   the single strongest argument for the RLS backstop in §5.

---

## 2. Choose the tenancy model first

Everything else follows from this. Three candidates:

| Model | Fit here | Verdict |
|---|---|---|
| **Database per tenant** | Strongest isolation, trivial per-tenant restore and export | **No.** Decision 7 is one DigitalOcean droplet at ₹2–3k/mo. Connection pools alone (`maximum-pool-size: 16` today) don't divide across tenants, and Flyway would run N times per deploy. Revisit only for a large enterprise customer who contractually demands it. |
| **Postgres schema per tenant** | Good isolation, single DB | **No.** Collides head-on with schema-per-module: 9 modules × N tenants schemas, and migrations would need to be applied per tenant per module. It would destroy the cleanest thing about this codebase. |
| **Shared schema, `tenant_id` column, Postgres RLS** | One DB, one set of migrations, module boundaries untouched | **Recommended.** |

**Recommendation: shared schema + `tenant_id` + row-level security.**

RLS is doing real work here, not ceremony. It is the backstop that catches the
raw-SQL paths, a forgotten `WHERE` clause in a new repository method, and
`findById` on an id guessed from another tenant. The application still filters
explicitly — RLS is defence in depth, not the primary mechanism — but it is what
turns "we were careful" into "the database refuses."

---

## 3. Tenant ≠ store (decide this before writing a migration)

The tempting shortcut is to treat `serviceability.stores.id` as the tenant id.
It is already on `orders` and `inventory`, so it looks free.

**It is the wrong anchor.** A tenant is the business that pays the subscription;
a store is a physical outlet with coordinates, opening hours and its own stock.
The moment a customer with two outlets signs up, that conflation means two
subscriptions, two catalogs to maintain in parallel, two logins for the same
owner, and no combined reporting. A quick-commerce product that cannot sell to
a two-outlet grocer has a fairly small market.

So: introduce `tenant` as a new concept **above** store.

```
tenant (1) ──< store (N)          -- a chain is one tenant, many outlets
tenant (1) ──< user (N)           -- staff and customers belong to a tenant
tenant (1) ──< product (N)        -- catalog is per tenant, shared across its stores
store  (1) ──< stock_level (N)    -- stock stays per outlet (already true)
```

Day one every tenant has exactly one store, so nothing in the UI has to change
— but the model does not have to be torn up when it doesn't.

**Where it lives:** a new `platform` module owning tenant CRUD, onboarding,
plans and billing. It depends on other modules; they must not depend on it, or
`ModularityTests` will report a cycle. The *context holder* (`TenantContext`) is
different — every module needs it, so it belongs in `shared`, which is already
`@ApplicationModule(type = OPEN)` for exactly this reason.

---

## 4. Schema changes, module by module

Each stays inside its existing Flyway band. Every migration is three steps:
add the column nullable → backfill to tenant 1 → set `NOT NULL` + add the RLS
policy. Existing production data all belongs to one tenant, so the backfill is
a constant.

| Band | Migration | Change |
|---|---|---|
| shared `V1_1` | tenant registry | `shared.tenants` (id, slug, name, status, plan, created_at); `shared.tenant_domains` (host → tenant, for §8 host routing); helper `shared.current_tenant()` reading the `app.tenant_id` GUC |
| identity `V2_7` | tenant scoping | `users.tenant_id`; drop global `UNIQUE(phone)` / `UNIQUE(email)` → `UNIQUE(tenant_id, phone)` / `UNIQUE(tenant_id, email)`; `addresses.tenant_id`, `refresh_tokens.tenant_id` |
| catalog `V3_10` | tenant scoping | `categories.tenant_id`, `products.tenant_id`, `product_variants.tenant_id`; drop global slug uniques → `UNIQUE(tenant_id, slug)`; rebuild the FTS + trigram indexes leading with `tenant_id` |
| inventory `V4_4` | tenant scoping | `stock_levels.tenant_id` (store_id stays — it's the outlet), `reservations.tenant_id`, `stock_movements.tenant_id` |
| cart `V5_3` | tenant scoping | `carts.tenant_id`, `cart_items.tenant_id` |
| orders `V6_12` | tenant scoping | `orders.tenant_id`, `order_items.tenant_id`, `order_events.tenant_id`; `idempotency_key` unique → `UNIQUE(tenant_id, idempotency_key)`; `invoice_number` unique → `UNIQUE(tenant_id, invoice_number)` |
| orders `V6_13` | invoice series | `invoice_series` PK `fy` → PK `(tenant_id, fy)`. **Legally load-bearing**: each tenant is its own taxable person under GST and needs its own consecutive per-FY series (CGST Rule 46(b)), as the V6_9 comment explains for the single-tenant case |
| payments `V7_2` | tenant scoping | `payments.tenant_id` |
| serviceability `V8_6` | tenant scoping | `stores.tenant_id`; drop the "one active store" assumption — active becomes per tenant |
| notifications `V9_3` | tenant scoping | `notification_log.tenant_id`, `push_subscriptions.tenant_id` |
| all | RLS | `ALTER TABLE … ENABLE ROW LEVEL SECURITY; FORCE ROW LEVEL SECURITY;` + `USING (tenant_id = shared.current_tenant())` policy per table |

Notes on specific columns:

- **`orders.public_code`** (V6_8) can stay globally unique. It costs nothing and
  removes ambiguity when a customer reads a code down the phone. Worth noting
  that V6_8's rationale — a sequential id leaks order volume — now applies
  across tenants too; the random base32 code already handles that.
- **`tenant_id` on child tables** (`order_items`, `cart_items`, …) is
  denormalised: it is derivable from the parent. Carry it anyway. RLS policies
  need it on the table they protect, and a join-based policy is both slower and
  easier to get wrong.
- **Seed migrations are now fixtures, not onboarding.** `V2_2` (staff), `V3_2`
  (catalog), `V4_2` (stock) and `V8_2` (store) create "the" store's data. They
  become tenant-1 fixtures. **Onboarding a new tenant must be application code**
  — a `platform` service that creates the tenant, its first store, its admin
  user and an empty catalog. Never Flyway.

---

## 5. Resolving and enforcing tenant context

### Resolution

Two distinct paths, and both are needed:

1. **Authenticated requests** — a `tenant` claim in the JWT, alongside the
   existing `role` claim (`JwtTokenService.java:85`). Signed, so it cannot be
   tampered with; this is what replaces the `?storeId=` parameters.
2. **Anonymous requests** — catalog browsing, serviceability checks and cart
   operations are `permitAll` (`SecurityConfig.java:112`) and have no token.
   These resolve by **`Host` header** against `shared.tenant_domains`.

A request that resolves to neither is rejected, except for the small allowlist
of genuinely global endpoints (`/actuator/health`, platform auth).

**Guard the seam between them:** when a request has both a token and a host,
they must agree. A token minted for tenant A arriving on tenant B's host is a
401, not a "prefer the token." Otherwise the host-based path becomes a way to
launder a stolen token into another tenant's session.

### Enforcement

- `TenantContext` in `shared` — a `ThreadLocal<Long>`, set by a servlet filter.
- **Filter ordering matters.** The tenant filter must run *after*
  `JwtAuthenticationFilter` (it needs the parsed claim) but *before* anything
  that touches data. `RateLimitFilter` is per-IP and can stay where it is,
  though see the noisy-neighbour note in §9.
- The same filter sets the Postgres GUC (`SET LOCAL app.tenant_id = …`) on the
  transaction, which is what the RLS policies read.
- Application code still filters explicitly. RLS is the net, not the floor.

### Four places the ThreadLocal will silently fail

These are the bugs this migration will actually produce, so they're worth
listing before they happen:

1. **Modulith event listeners.** Events are consumed on a different thread from
   the publisher. `shared/events/*` records (`OrderPlaced`, `OrderConfirmed`, …)
   already carry `storeId` — they need `tenantId` too, and each listener must
   re-establish the context from the event rather than inheriting it.
2. **Outbox republish on restart.** `republish-outstanding-events-on-restart:
   true` replays `public.event_publication` rows at boot, with no request and
   no ThreadLocal at all. The tenant must be recoverable from the serialised
   event payload — which point 1 gives you, provided the field is added
   *before* any multi-tenant events are ever written.
3. **Scheduled jobs.** `RefreshTokenCleanup` and `ProductNameBackfillJob` run on
   a timer with no tenant. They need either an explicit per-tenant loop or a
   system role that bypasses RLS. Choose deliberately: a bypass role that leaks
   into a request path defeats the whole design.
4. **Caches.** `CacheConfig` sizes **both** its caches at **1 entry** —
   `activeStore` and `categories` (`CacheConfig.java:75-77`). Unchanged, the
   first tenant to load each one serves it to every other tenant: their store
   settings, and their category navigation on the customer-facing storefront.
   A cross-tenant data leak with no error and no log line. Every cache key
   needs a tenant dimension and every `maximumSize` needs re-sizing for N
   tenants. This deserves a dedicated test.

---

## 6. Per-tenant configuration

Today everything is a global env var (`application.yml`). The split:

**Must become per-tenant rows** (a tenant cannot share these with another):

| Setting | Today | Why per-tenant |
|---|---|---|
| GSTIN | `TOWNBASKET_INVOICE_GSTIN` | Each tenant is a separate taxable person. Legal requirement |
| Invoice series prefix | `TOWNBASKET_INVOICE_SERIES_PREFIX` (`TB`) | Per-business series; still ≤4 chars for the Rule 46(b) 16-char cap |
| Store coords, radius, hours, min order, support phone | `serviceability.stores` row | Already per-store ✓ |
| Brand name, logo, theme, domain | Hardcoded in the frontends | §8 |
| Payment merchant credentials | Not yet wired (UPI is a fake, `upi-enabled: false`) | Money must settle to *their* account — see the flag in §11 |
| Product image bucket prefix | One global bucket | §9 has the deletion-guard trap |

**Stays platform-global:** DB connection, JWT signing secret, Firebase project
id (one project can verify phone tokens for all tenants — the tenant comes from
the host, not the token), transliteration endpoint, CORS policy source.

**Genuinely ambiguous, decide explicitly:**

- **`tax`** — HSN codes and GST rates are set by statute, not by the merchant,
  so the reference data is legitimately global. But *which* HSN a tenant assigns
  to their product is per-tenant, and it already lives on `catalog`, which is
  being scoped. Leaving `tax` global is probably right; it should be a recorded
  decision rather than an oversight.
- **Web Push VAPID keys** — VAPID identifies the *application server*, and with
  per-tenant origins (§8) each tenant's service worker is its own origin.
  Sharing one key pair across origins will work but couples all tenants'
  push identity to one key; rotating it breaks every tenant at once. Lean
  per-tenant, at the cost of a key-generation step in onboarding.

---

## 7. Identity: the hardest product question

`identity.users` has globally `UNIQUE` `phone` and `email`. Changing that forces
a question the schema currently doesn't ask: **is a customer a customer of the
platform, or of a store?**

| | Customer scoped per tenant | Customer global across tenants |
|---|---|---|
| Uniqueness | `UNIQUE (tenant_id, phone)` | `UNIQUE (phone)`, join table for membership |
| Same person, two stores | Signs up twice, two order histories | One login, switches store |
| Privacy | Store A cannot learn the customer shops at B | Platform knows; needs careful exposure rules |
| RLS | Trivial | `users` needs a different policy from everything else |
| Later change | Merging accounts is hard but possible | Splitting is very hard |

**Recommendation: scoped per tenant.** It matches how a neighbourhood grocer
thinks about their customer list, keeps the RLS story uniform, and avoids
leaking shopping relationships between competing stores. The cost — signing up
twice — is small when the tenants are geographically separated supermarkets,
which is exactly this product's market.

Two more identity changes:

- **A new role above `ADMIN`.** Today `ADMIN` is the top role
  (`SecurityConfig.java:79`) and means "runs this store." SaaS needs
  `PLATFORM_ADMIN` — you, operating the platform — as a distinct role that is
  *not* a tenant role and is the only thing allowed to cross tenant boundaries.
  Do not overload `ADMIN`.
- **Firebase phone lookup** becomes `(tenant, phone)`. One Firebase project
  still serves everyone; the verifier returns a phone number, and the tenant
  comes from the host. The `FakePhoneTokenVerifier` dev path
  (`dev:<10-digit-phone>`) needs the same treatment or local multi-tenant
  testing is impossible.

---

## 8. Frontend and deployment

Three Next.js apps (`storefront`, `admin`, `delivery`), 22 hardcoded
"Town Basket" strings in `.ts`/`.tsx`, and 53 references to `town-basket.com`
across `apps/` and `infra/`. The `Caddyfile` hardcodes four subdomains.

**Container topology: one set of containers serving all tenants.** Not a
container set per tenant. At ₹2–3k/mo for the whole droplet, per-tenant
containers make the unit economics impossible before the second customer.

**Domains — two options, and they're not exclusive:**

1. **Subdomain per tenant** (`kumar-stores.town-basket.com`). One wildcard
   certificate, trivial onboarding, works on day one.
2. **Custom domains** (`kumarstores.com`). Needed by anyone who takes their
   brand seriously, and a natural paid-plan upsell. Caddy's `on_demand_tls`
   handles this well, but it needs an `ask` endpoint — an API route that
   answers "is this host a known tenant domain?" against `shared.tenant_domains`.
   Without that endpoint, on-demand TLS is an open certificate-issuance relay.

Ship (1) first; (2) is additive and needs only the ask endpoint plus a DNS
verification step in onboarding.

**Next.js changes:**

- `middleware.ts` in each app reads `Host`, resolves the tenant, and forwards it
  as a request header so server components can read it without re-resolving.
- Branding comes from an API call, cached. The 22 hardcoded strings become
  references to a tenant theme object.
- `app/manifest.ts` becomes host-aware — each tenant's PWA needs its own name,
  icons and theme colour, or installed apps all claim to be Town Basket.
- `app/sw.ts` / Serwist scope is per origin, which the subdomain model gives
  you for free.
- `INTERNAL_API_BASE_URL` SSR calls bypass Caddy (noted in `application.yml`),
  so the tenant must travel as an explicit header on that path — the `Host` the
  API sees is `api:8080`.

---

## 9. Things that will break quietly

The list of traps found while reading, roughly in order of how expensive each
would be to discover in production:

1. **Both caches sized 1** (`activeStore`, `categories`) — §5, point 4.
   Cross-tenant leak, no error, one of them on the customer-facing storefront.
2. **Client-supplied `storeId`** — §0. Must be deleted, not defaulted.
3. **Image deletion guard** — `ProductImageStorage` decides "is this image ours
   to delete" from `public-base-url`. With one shared bucket, tenant A can pass
   a URL under that base pointing at tenant B's object. The guard must check a
   per-tenant prefix, not just the bucket origin.
4. **Global slug uniqueness** — tenant B cannot create `amul-dahi` because
   tenant A did. A confusing, arbitrary-looking failure at exactly the moment a
   new customer is setting up.
5. **`idempotency_key` global unique** — a collision across tenants silently
   rejects a legitimate order as a duplicate.
6. **`invoice_series` keyed on `fy` alone** — two tenants sharing one counter
   produces gapped, non-consecutive series for both. Non-compliant invoices,
   discovered at audit.
7. **`ModularityTests` will fail the build** if `TenantContext` lands in the
   wrong module. `shared` (OPEN) is the only legal home for the context holder;
   `platform` for tenant administration. Treat a boundary failure as real, per
   CLAUDE.md.
8. **Integration tests need a two-tenant fixture.** `AbstractIntegrationTest`
   gives a shared singleton container. The valuable test here is uniform and
   mechanical: for every module, seed two tenants, act as tenant A, assert
   tenant B's rows are invisible to reads *and* unreachable by direct id. Write
   it once as a reusable base and apply it everywhere.
9. **Per-tenant restore becomes an export, not a restore.** Nightly backups are
   whole-DB dumps (decision 7a). "Tenant X wants their data back as of
   Tuesday" is now a filtered extract from a restored copy, not a DB restore.
   This is a genuine ops regression against a shared-schema model and should be
   priced in.
10. **SSE connection registry** (`NotificationStreamController`) must key by
    tenant as well as order, and the connection count on one droplet now scales
    with the number of tenants, not just customers.
11. **Rate limiting is per-IP, in-memory** with no tenant dimension. One
    tenant's traffic spike consumes shared capacity. Not urgent at launch;
    worth a note before tenant three.
12. **Delivery queue is not store-filtered** — riders would see every tenant's
    orders.
13. **FTS and trigram indexes** should lead with `tenant_id`, or every tenant's
    search scans every tenant's catalog.

---

## 10. Phasing

Each phase is independently shippable. The ordering is not arbitrary: **P0 must
be complete before a second tenant exists anywhere, including staging.**
Retrofitting isolation onto live multi-tenant data means a migration under
correctness pressure with real customers' data mixed together.

**P0 — Isolation foundation.** *Still single-tenant when it ships; nothing
user-visible changes.*
- `shared.tenants` + `tenant_domains`, `TenantContext` in `shared`
- `tenant` claim in the JWT; tenant resolution filter
- `tenant_id` on every table, backfilled to 1, `NOT NULL`, RLS enabled
- Delete every client-supplied `storeId` parameter (6 call sites)
- `resolveActiveStoreId()` reads the context instead of returning `1L`
- Tenant-dimension every cache key; re-size `activeStore` and `categories`
- `tenantId` on every event record; outbox replay recovers it
- Two-tenant isolation test per module

**P1 — Second tenant possible (operator-provisioned).**
- `platform` module: tenant CRUD, onboarding service that creates tenant +
  store + admin user + empty catalog
- Per-tenant config: GSTIN, invoice prefix, branding row
- `PLATFORM_ADMIN` role
- Scheduled jobs made tenant-aware

**P2 — White-label frontends.**
- De-hardcode the 22 brand strings; tenant theme from API
- Host→tenant middleware in all three apps; host-aware manifests
- Wildcard subdomain routing in Caddy

**P3 — Self-serve.**
- Signup flow, plan/subscription model, billing integration
- Custom domains: Caddy `on_demand_tls` + the ask endpoint + DNS verification

**P4 — Platform console.** Tenant list, per-tenant health, impersonation
(audited), usage metering.

**P5 — Scale and ops.** Per-tenant export/restore tooling, noisy-neighbour
limits, per-tenant observability, and the point at which decision 7 (one
droplet) gets revisited.

---

## 11. Risks and honest caveats

**The market is narrower than "e-commerce SaaS" sounds.** This codebase is
deeply specific: Indian GST invoicing, Kannada transliteration, UPI/COD,
Haversine radius delivery, packaged-goods-only catalog. That specificity is an
*asset* for a vertical SaaS — it is precisely what a generic Shopify cannot do
for a Bengaluru neighbourhood supermarket — but the addressable market is
"Indian neighbourhood grocers wanting their own quick-commerce app," not
retail at large. Worth being clear-eyed about before investing P0–P3 of effort.

**Onboarding will not be fully self-serve on day one.** A new tenant needs a
GSTIN, a payment merchant account, a domain, product data and stock counts.
Realistically that is an assisted onboarding for the first many customers,
which caps how fast you can grow and means P3 matters less than it looks.

**Payments needs a real answer, and possibly a regulatory one.** UPI is
currently a fake provider with `upi-enabled: false`, so today nothing collects
money. In a SaaS, money paid by a customer has to settle to *the tenant's*
account, not yours. Routing third-party funds in India can require payment
aggregator authorisation, or a gateway offering marketplace split settlement
with each tenant onboarded as a sub-merchant. **This is a "get it checked before
building it" flag, not legal advice** — but it is the item most likely to
invalidate a launch plan, and it is cheap to resolve early and expensive to
discover late.

**Availability expectations change.** One store tolerating "minutes to a couple
of hours" of downtime (decision 7a) is a considered trade-off by the business
that owns the store. Twenty paying tenants sharing one droplet with no failover
is a different proposition, and the first outage will be the conversation. P5,
or earlier if tenant count climbs.

---

## 12. Decisions needed before P0 starts

1. **Tenant vs store** — accept the separate `tenant` concept (§3), or accept
   the ceiling of one outlet per customer?
2. **Customer identity** — per-tenant or global (§7)? Hard to reverse.
3. **Domain strategy** — subdomains only at first, or custom domains in scope
   from the start (§8)?
4. **`tax` module** — confirm global reference data is correct, and record it.
5. **VAPID keys** — shared or per-tenant (§6)?
6. **Payments model** — who is the merchant of record (§11)? This gates any
   launch that takes money.

Answer 1, 2 and 6 and P0 can start; the rest can be decided during it.
