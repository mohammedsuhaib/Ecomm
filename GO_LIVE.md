# Production go-live: what still has to change

**Status:** assessment, nothing implemented.

**How to read this.** Part A applies to **any** production launch, including the
current single-store Town Basket — these are gaps in what exists today. Part B is
the **additional** work a multi-tenant SaaS launch needs on top of Part A (it
assumes the sequence in [`SAAS.md`](./SAAS.md)). Part C is the non-code
readiness that tends to be discovered late. Part D is a cutover order.

Part A is not optional for Part B. You cannot launch a SaaS on a base that
ships known credentials.

**What is already done**, so it doesn't get redone: the deploy path is real and
more complete than `README.md` suggests. `.github/workflows/deploy-app.yml`
builds and pushes four images to GHCR, SSHes to the droplet, pulls pinned
`sha-<commit>` tags and health-checks all four subdomains. `docker-compose.prod.yml`
guards every must-have variable with `${VAR:?…}` so a half-configured stack
refuses to start, and budgets memory per container. Caddy does auto-TLS for four
subdomains. `backup/nightly-backup.sh` dumps, gzips, uploads to Spaces with
`--acl private` and prunes by retention. `infra/deploy/.env.example` marks
required vs optional vs build-time. Rate limiting, login throttling and the
`PhoneVerifierMode` boot guard are all in place. That's a lot of the hard part.

---

## Part A — Blockers for any production launch

### A1. Seeded accounts with repo-committed passwords are live in production 🔴

The single most urgent item.

`db/migration/identity/V2_2__identity_seed.sql`, `V2_3` and `V2_4` create three
accounts whose plaintext passwords are written in the migration comments:

| Account | Password | Role |
|---|---|---|
| `admin@townbasket.local` | `Admin@12345` | `ADMIN` — full access, including the staff directory |
| `staff@townbasket.local` | `Staff@12345` | `STORE_STAFF` |
| `delivery@townbasket.local` | `Delivery@12345` | `DELIVERY_AGENT` |

These migrations are **not gated by anything**. `application.yml` lists
`classpath:db/migration/identity` unconditionally in `flyway.locations`, there is
no `application-prod.yml`, and neither the prod compose file nor
`deploy-app.yml` sets `SPRING_PROFILES_ACTIVE`. So they run on every
environment, production included, and the accounts exist right now unless
somebody deactivated them by hand.

The mitigations already in the codebase do not help here. `LoginAttemptLimiter`
bounds *failed* attempts (10 / 5 min); a correct password on the first try is
not a failed attempt. And `Admin@12345` is guessable without ever seeing the
repo.

**Fix, in two parts:**

1. **Immediate** — a forward migration (`V2_7`) that deactivates and
   de-credentials the three `@townbasket.local` rows (`active = FALSE`,
   `password_hash = NULL`) in every environment. Prefer deactivating to
   deleting: `identity.refresh_tokens` and `identity.addresses` have real FKs to
   `users`, and `orders` carries `user_id` as a loose reference, so a delete can
   fail or orphan history. Never edit `V2_2` in place — Flyway checksum
   validation would fail the boot, exactly as the `V2_4` comment explains.
2. **Structural** — move dev fixtures out of the migration path entirely.
   Relocate them to `classpath:db/seed/dev` and add that to `flyway.locations`
   only under a `local`/`test` profile, so a production Flyway run cannot apply
   a fixture even by accident. This is the change that stops the next seed from
   repeating the problem.

Then create the real staff accounts through `AdminDirectoryController`
(`/api/v1/admin/staff`), which already exists, with passwords set by whoever
will use them.

### A2. Demo catalogue and fictional stock are also seeded unconditionally

Same root cause, lower severity, but it makes the store look broken on day one.

- `V3_2__catalog_seed.sql` — 312 lines of demo categories, products and
  variants, with invented `selling_price` **and `cost_price`** (the latter feeds
  gross-profit reporting, so fake values silently corrupt the margin numbers
  staff will look at).
- `V4_2__inventory_seed.sql` — sets `on_hand = 100` for *every* seeded variant.
  Customers can order 100 units of products the store may not carry.

**Fix:** same structural move as A1 (dev-only seed location), plus a decision on
existing prod data: either wipe the demo catalogue and let staff enter the real
one, or keep the rows and have staff correct prices and stock. Wiping is
cleaner, and needs checking against any orders already placed against those
variants — `orders.order_items` snapshots `product_name`/`unit_price`, so
history survives a catalogue delete, which is the useful thing here.

### A3. Store row holds placeholder coordinates

`V8_2__serviceability_seed.sql` seeds lat/lng `12.21, 76.89` with the comment
"approximate coordinates … client to confirm exact coords." The delivery radius
gate is a Haversine distance from this point
(`ServiceabilityServiceImpl.check`), so an imprecise origin means the 5 km
boundary is wrong by however far off the seed is — refusing real customers
inside the radius, or accepting ones outside it.

Confirm the storefront coordinates and set them, plus opening/closing hours,
`min_order_value` (seeded at ₹499) and `support_phone`, through the admin store
settings screen. Also verify `TOWNBASKET_SERVICEABILITY_STORE_LAT/_LNG` are
**unset** in prod `.env` — they are a testing override that silently replaces
the store's real coordinates.

### A4. There is no real payment provider 🔴

`payments/internal` contains `CodProvider` and `FakeProvider`. **There is no
`PaytmProvider`** — M5 in the README roadmap is not done. `FakeProvider` is
`@Primary` for UPI and returns `PaymentStatus.PAID` with reference
`FAKE-UPI-<orderId>` unconditionally.

`UPI_ENABLED` defaults to `false`, and both the config comment and the prod
compose comment correctly explain why. The risk is that the flag is the *only*
thing standing between a fake provider and orders marked PAID with no money
received — and it is a one-word change in `.env` by someone who reads
"UPI_ENABLED=false" as a feature toggle rather than a safety interlock.

**Two paths:**

- **Launch COD-only** (viable today — `CodProvider` is real, and Pay on Delivery
  is always available). Leave `UPI_ENABLED=false`.
- **Launch with UPI** — integrate a real gateway behind the existing
  `PaymentProvider` port: charge, webhook verification + signature check, the
  `payment_webhook_log` table the `V7_1` comment says arrives with the live
  flow, reconciliation, and refund handling on cancellation.

**Either way, add a boot-time guard**: refuse to start when `upi-enabled=true`
and the resolved UPI provider is `FakeProvider`. This is exactly the pattern
`PhoneVerifierMode` already uses for the Firebase fake — fail loudly at startup
with the property name and the fix, rather than silently accepting money that
never arrives. Cheap, and it closes the one-word-mistake path.

### A5. Swagger UI and the full API spec are publicly reachable 🟠

`springdoc-openapi-starter-webmvc-ui` is a runtime dependency (`pom.xml:175`),
nothing disables it, there is no prod profile, and `SecurityConfig` ends with
`anyRequest().permitAll()` — its own comment lists "swagger, actuator
health/info" as public. Caddy proxies `api.town-basket.com` → `api:8080`
wholesale, so `/swagger-ui/index.html` and `/v3/api-docs` are on the public
internet, publishing every endpoint, parameter and DTO shape.

**Fix:** set `springdoc.api-docs.enabled=false` and
`springdoc.swagger-ui.enabled=false` in production, or add an explicit
`.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").hasRole("ADMIN")` rule.
Disabling is simpler and CI still generates the spec from source.

### A6. `/actuator/metrics` is publicly reachable 🟠

`management.endpoints.web.exposure.include: health,info,metrics` plus the same
`permitAll` catch-all and the same wholesale Caddy proxy. `metrics` exposes JVM
internals and `http.server.requests` counters — which, per endpoint, leak order
throughput to anyone who polls them. That is the same information disclosure
`V6_8` went out of its way to prevent by replacing sequential order ids with
random codes.

**Fix, cleanest option:** move management to its own port
(`management.server.port: 8081`) and leave it out of the Caddyfile. The
container is `expose`d rather than `ports`-published, so 8081 stays inside the
Docker network and is reachable for internal health checks but not from
outside. Alternative: drop `metrics` from the exposure list and keep
`health,info`. Note `deploy-app.yml` health-checks `/actuator/health` through
the public hostname, so if you move the port, that check moves with it.

### A7. Invoices ship without a GSTIN

`TOWNBASKET_INVOICE_GSTIN` defaults to blank, and the config comment is explicit:
"GSTIN is hidden while blank — set it once the store is registered, since a tax
invoice must carry it." The invoice numbering machinery itself is properly built
(`V6_9`: per-FY consecutive series, transactional counter, immutable once
issued, Rule 46(b) 16-char cap respected).

Set `TOWNBASKET_INVOICE_GSTIN` and confirm `TOWNBASKET_INVOICE_SERIES_PREFIX`
(defaults to `TB`, must stay ≤4 chars) before the first invoice is issued — the
series is immutable once numbers are handed out.

### A8. Firebase phone OTP — configuration plus cost

Already well guarded: the prod compose hard-requires `FIREBASE_PROJECT_ID` with
`${VAR:?…}`, and `PhoneVerifierMode` fails the boot on a blank value rather than
silently falling back to the fake. Nothing to fix in code.

What remains is operational: a real Firebase project with phone auth enabled,
billing attached, the SMS quota and per-SMS cost understood, and the authorised
domains configured. Verify the fake verifier is genuinely inactive after deploy
by confirming a `dev:<phone>` token is rejected.

### A9. Two features are silently off unless configured

Both are deliberate "blank means off" designs, so they won't error — they'll
just be absent, which is worse to discover after launch:

- **Web Push** (`WEB_PUSH_PUBLIC_KEY` / `_PRIVATE_KEY` blank) → no browser
  notifications; the apps fall back to in-app SSE only, so a customer who closes
  the tab learns nothing about their order. Generate a VAPID pair and set
  `WEB_PUSH_SUBJECT` to a real mailbox. See `NOTIFICATIONS.md`.
- **Product image upload** (`CATALOG_IMAGES_*` blank) → the admin form degrades
  to pasting URLs, and staff cannot upload a photo from a phone. Needs a Spaces
  bucket **separate from the backup bucket** (these objects are world-readable;
  dumps must never be). Note `public-base-url` is immutable in practice —
  changing it later orphans every image written under the old one.

### A10. Backups exist as a script; they are not yet a working backup

`nightly-backup.sh` is solid. What's missing is everything around it:

- **The cron/timer is a manual one-time droplet step** (the schedule is a
  comment in the script header). Until someone installs it, there are no
  backups. This is the highest-consequence unchecked box on the list.
- **No alerting.** The deploy README says "Alert on backup success/failure" —
  nothing implements it. A silently failing backup is indistinguishable from a
  working one until you need it.
- **The restore runbook is described as tested but isn't written down.** Do a
  real restore into a scratch database and record the commands and the elapsed
  time. An untested backup is a hope.
- **`DB_URL_PG`** is a second, `pg_dump`-format copy of the DB credentials in
  `.env`; confirm it's populated and in step with `DB_URL`.

### A11. Monitoring is opt-in and there is no uptime alerting

The Grafana Alloy log shipper is behind `profiles: ["monitoring"]` and off
unless `COMPOSE_PROFILES=monitoring` is set. Even switched on it ships logs
only — there is no metrics scrape, no dashboard, and nothing that pages a human.

At minimum before launch: turn on log shipping, add external uptime checks on
the four subdomains (any third-party monitor), and alert on the two things that
mean lost orders — API 5xx rate, and container restarts. `leak-detection-threshold`
is already set to 20s so connection leaks log a warning; something has to be
reading those logs for that to matter.

### A12. `README.md` is materially wrong

It says "Status: Pre-development," lists Gradle as the build tool, shows a
`packages/api-client` that was deleted, and states "no delivery-rider app — by
design" while `apps/delivery` exists, is deployed by CI and has its own
subdomain in the Caddyfile. `CLAUDE.md` already warns readers not to trust the
README. This isn't cosmetic at
go-live: it is the first document a new developer, a client, or a security
reviewer opens. Bring it in line with what actually exists.

---

## Part B — Additional work for a SaaS go-live

On top of all of Part A. Detail and reasoning live in [`SAAS.md`](./SAAS.md);
this is the go-live view of it.

### B1. The P0 isolation work must be complete and verified

Non-negotiable and order-dependent: it has to land **before a second tenant
exists anywhere, staging included**. Retrofitting isolation onto live
multi-tenant data is a migration under correctness pressure with real
customers' records already mixed together.

Go-live gate: `tenant_id` on every table with RLS enabled and forced; the
`tenant` JWT claim; the six client-supplied `storeId` parameters deleted;
`resolveActiveStoreId()` reading context instead of returning `1L`; both
`CacheConfig` caches tenant-keyed and re-sized; `tenantId` on every event and
recoverable from the outbox on restart. Plus the two-tenant isolation test per
module — for a SaaS this is a release gate, not a nice-to-have.

### B2. Tenant onboarding replaces the seed migrations

A1 and A2 stop being "remove the fixtures" and become "build the thing that
replaces them." A `platform` onboarding service creates the tenant, its first
store, its admin user with a password the customer sets, and an **empty**
catalogue. Flyway must never create tenant data.

### B3. Per-tenant configuration must exist before the second tenant

Every item in Part A that is currently one global env var becomes a per-tenant
row: GSTIN (A7 — each tenant is its own taxable person, so a shared GSTIN is
not merely wrong but non-compliant), invoice prefix and its per-FY counter,
branding, payment credentials, image bucket prefix, and VAPID keys if you go
per-tenant. Tenant two cannot be onboarded until this exists, because there is
nowhere to put their GSTIN.

### B4. Host routing and certificates

Wildcard DNS and a wildcard certificate for subdomain-per-tenant; the
`shared.tenant_domains` lookup; host→tenant middleware in all three Next apps;
host-aware PWA manifests. If custom domains are in scope at launch, Caddy
`on_demand_tls` plus the `ask` endpoint that validates the host against known
tenant domains — **without that endpoint, on-demand TLS is an open
certificate-issuance relay.**

### B5. Billing and subscription lifecycle

Plans, the subscription record, invoicing for the subscription itself (with GST
on the SaaS fee — separate from the tenant's own GST invoicing), payment
failure handling, and a defined suspension state. Decide what a suspended
tenant's storefront shows: their customers are not the party in arrears, and a
blank page or a raw error is a bad look for both of you.

### B6. Platform operations

A `PLATFORM_ADMIN` console: tenant list, per-tenant health, and impersonation
for support. **Impersonation must be audited** — it is read and write access to
a customer's business data, and an unlogged admin-as-tenant session is a finding
in any review a serious customer runs on you.

### B7. Per-tenant backup, export and deletion

A10's whole-DB dump no longer answers the questions a tenant will ask. "Give me
my data" and "delete my data" become filtered extracts from a restored copy —
tooling that has to be built, and a documented ops regression against the
shared-schema model (SAAS.md §9.9). Under India's DPDP Act these are requests
you are obliged to answer on a timeline, so the tooling is not optional.

---

## Part C — Non-code readiness

Flagging these because they are commonly found late, and two of them can
invalidate a launch date:

- **Payments licensing** 🔴 — routing customer money to a *tenant's* account can
  require payment-aggregator authorisation, or a gateway offering marketplace
  split settlement with each tenant onboarded as a sub-merchant. Get this
  checked before building B5. **This is a "have it reviewed" flag, not legal
  advice** — but it is the item most likely to invalidate a launch plan, and it
  is cheap to resolve early and expensive to discover late.
- **Data protection** — India's DPDP Act 2023 brings consent, retention limits,
  breach notification and grievance-redressal obligations. You hold customer
  phone numbers, addresses and precise lat/lng. As a SaaS you are additionally
  processing on tenants' behalf, which needs a written arrangement with each of
  them. Worth a proper review, not a template.
- **Public policy pages** — terms, privacy policy, refund/cancellation policy,
  contact and grievance details. Payment gateways generally require these live
  before they'll approve a merchant account, so this can gate A4.
- **Support** — a real channel with a named owner and a response expectation.
  `WEB_PUSH_SUBJECT` currently defaults to `support@town-basket.com`; confirm
  that mailbox is monitored.
- **UAT** — `TEST_CASES.md` exists; run it against the QA environment
  (`deploy-qa.yml`) with real staff on real devices, and specifically on a
  low-end Android phone on a mobile connection, which is the target device.
- **Rollback rehearsal** — the documented rollback (re-run compose pinned to a
  previous `sha-` tag) should be executed once in QA rather than first attempted
  during an incident. Note it does not roll back Flyway migrations, so a
  forward-only discipline on schema changes is part of the plan.

---

## Part D — Cutover order

**Ship before anything else touches production:**

1. A1 — kill the seeded accounts (forward migration + move fixtures behind a profile)
2. A5, A6 — close the public Swagger, api-docs and metrics surfaces
3. A4 — the `upi-enabled` + `FakeProvider` boot guard

These three are small, independent, and each closes a hole that exists in
production right now. They are worth a single focused change set.

**Then, before taking real orders:**

4. A2, A3 — real catalogue and stock; confirmed store coordinates and settings
5. A7, A8 — GSTIN and invoice prefix; real Firebase project verified live
6. A10 — backup timer installed, alerting wired, restore rehearsed
7. A9 — decide push and image upload; configure or consciously defer
8. A11 — log shipping on, uptime checks and 5xx alerting live
9. Part C — policy pages, support mailbox, UAT, rollback rehearsal
10. A12 — fix the README

**Only then, if going SaaS:** Part B, in the SAAS.md P0→P3 order, with B1
complete and its isolation tests green before tenant two exists anywhere.

---

## Summary

The deployment machinery is largely built. What is missing is not infrastructure
— it is the set of dev fixtures and open dev surfaces that were never closed off
for production, and one genuinely unbuilt feature (payments).

Three items are live holes today: **seeded admin credentials published in the
repo** (A1), **a public Swagger UI, API spec and metrics endpoint** (A5, A6),
and **a fake payment provider one `.env` word away from marking orders paid**
(A4). Everything else on this list is configuration, verification or a
deliberate scope decision.
