# Town Basket

A quick-commerce platform for a single supermarket, delivering groceries
within a configurable radius (default 5 km) of the store. Live at
**town-basket.com**. Customers order
through an installable Progressive Web App; store staff manage the catalogue
and fulfil orders through a web dashboard. No native mobile app and no
delivery-rider app — by design.

> **Status:** Pre-development. The system design is finalised in
> [`ARCHITECTURE.md`](./ARCHITECTURE.md); implementation begins at milestone M1.

---

## What it does

**Customer PWA**
- Installable, offline-capable, fast on low-end mobile devices
- Serviceability check — enforces the delivery radius before ordering
- Browse by category, search, cart, checkout
- Phone-OTP login and a saved delivery address
- Online UPI payment via Paytm Payment Gateway (opens the customer's UPI app)
- Live in-app order status tracking and order history

**Store Admin Dashboard**
- Live order queue with status management
- Catalogue management (categories, products, variants, prices, images)
- Inventory management with bulk corrections and low-stock indicators
- Store configuration (delivery radius, hours) and daily order overview

Features beyond this core set (push/SMS/WhatsApp notifications, coupons,
loyalty, delivery slots, analytics, Cash on Delivery, multi-store, POS
integration) are designed-for but built as add-ons — see `ARCHITECTURE.md`.

---

## Architecture at a glance

A **modular monolith** built with **Spring Boot 3 + Spring Modulith (Java 21)**,
split into strictly-bounded modules — `identity`, `catalog`, `inventory`,
`cart`, `orders`, `payments`, `serviceability`, `notifications`, `shared`.
Modules communicate only through published APIs and domain events
(transactional outbox via the Modulith event registry), and each owns its own
Postgres schema. This keeps the system loosely coupled and lets any module be
extracted into a standalone service later without a rewrite.

Two **Next.js (TypeScript)** frontends (storefront + admin) consume the backend
through a TypeScript client generated from the OpenAPI spec, so the API
contract cannot silently drift between Java and TypeScript.

Everything runs on a single DigitalOcean droplet (Bangalore) via Docker
Compose, with Caddy for reverse-proxy + auto-TLS and nightly Postgres
backups to DO Spaces — cost-optimised for the store's scale.

See **[`ARCHITECTURE.md`](./ARCHITECTURE.md)** for the full design — module
responsibilities, data ownership, event contracts, failure modes, scaling
strategy, and key decisions.

---

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3, Spring Modulith, Java 21, Gradle |
| Database | PostgreSQL (schema per module), Flyway migrations |
| Frontend | Next.js, React, TypeScript, Serwist (PWA) |
| API contract | springdoc OpenAPI → generated TypeScript client |
| Auth | Phone OTP via Firebase Auth; own JWTs |
| Payments | Paytm Payment Gateway — UPI (behind a `PaymentProvider` port) |
| Infrastructure | DigitalOcean droplet, Docker Compose, Caddy (auto-TLS), DO Spaces |
| CI/CD | GitHub Actions |

---

## Planned repository layout

```
.
├── apps/
│   ├── api/          # Spring Boot modular monolith
│   ├── storefront/   # Next.js customer PWA
│   └── admin/        # Next.js admin dashboard
├── packages/
│   └── api-client/   # TypeScript client generated from OpenAPI
├── infra/
│   ├── docker-compose.yml   # local dev: Postgres + api + apps + seed data
│   └── deploy/              # DigitalOcean: compose.prod, Caddyfile, backups
├── .github/workflows/       # CI/CD
└── ARCHITECTURE.md
```

---

## Getting started (local development)

> These steps describe the intended local setup; tooling lands in milestone M1.

**Prerequisites:** JDK 21, Node.js 20+, Docker, Docker Compose.

```bash
# Start Postgres, the API, both frontends, and seed data
docker compose -f infra/docker-compose.yml up

# Storefront:  http://localhost:3000
# Admin:       http://localhost:3001
# API:         http://localhost:8080
```

---

## Build roadmap

| Milestone | Scope |
|---|---|
| M1 | Monorepo skeleton, Modulith app + boundary tests, Postgres schemas, CI |
| M2 | Catalogue, search, 5 km serviceability gate, installable PWA shell |
| M3 | Cart, checkout, order state machine, admin queue — first end-to-end order |
| M4 | Phone-OTP login, saved address, order history, staff roles |
| M5 | Paytm PG UPI payments live |
| M6 | DigitalOcean deployment, backups, monitoring, UAT, go-live |

---

## License

Proprietary. All rights in the delivered software transfer to the client on
full payment per the development agreement.
