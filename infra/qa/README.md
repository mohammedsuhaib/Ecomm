# QA environment — qa.town-basket.com

A full, internet-reachable copy of the Town Basket stack for testing before
production: storefront + admin + delivery + API + its own Postgres, built
**from source** on the droplet (no container registry needed), fronted by
Caddy with automatic HTTPS.

| URL | App | Protected by |
|---|---|---|
| https://qa.town-basket.com | Storefront | basic auth + app login |
| https://qa-admin.town-basket.com | Admin | basic auth + app login |
| https://qa-delivery.town-basket.com | Delivery | basic auth + app login |
| https://qa-api.town-basket.com | API | JWT roles (same as prod) |

**QA deliberately keeps the dev conveniences** that production must not have:
the offline fake phone verifier (log in with token `dev:<10-digit-phone>`),
the fake UPI gateway, and the seeded `admin@` / `staff@` / `delivery@`
accounts. That's what makes end-to-end testing possible without real money or
SMS. The three app hosts sit behind HTTP **basic auth** and send
`X-Robots-Tag: noindex` so the test site is neither browsable by strangers nor
indexed.

## One-time setup

1. **Droplet**: a $6–12/mo DigitalOcean droplet (Bangalore), Marketplace
   "Docker on Ubuntu" image. The from-source build needs ~2 GB RAM; on the
   smallest droplet add swap first:
   ```bash
   fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
   ```
2. **DNS** — four A records → the QA droplet IP:
   `qa`, `qa-admin`, `qa-api`, `qa-delivery` (all under town-basket.com).
3. **Firewall**: `ufw allow 80 && ufw allow 443 && ufw allow OpenSSH && ufw --force enable`
4. **Clone + secrets**:
   ```bash
   git clone https://github.com/mohammedsuhaib/Ecomm.git && cd Ecomm/infra/qa
   cp .env.example .env && chmod 600 .env
   # fill DB_PASSWORD, JWT_SECRET, and the basic-auth hash:
   docker run --rm caddy:2-alpine caddy hash-password --plaintext 'your-qa-password'
   ```
   Paste the resulting `$2a$...` hash into `QA_BASIC_AUTH_HASH` as-is.
5. **Launch**:
   ```bash
   docker compose -f docker-compose.qa.yml up -d --build
   ```
   First build takes several minutes (Maven + three Next builds). Flyway
   creates and seeds the QA database on API startup.

## Deploying a new version to QA

```bash
cd Ecomm && git checkout main && git pull
cd infra/qa && docker compose -f docker-compose.qa.yml up -d --build
```
Only changed images rebuild (Docker layer cache). To wipe QA data and start
fresh: add `down -v` before the `up`.

## Loading test data

The dev SQL scripts work on QA too, but note the compose file: QA is a separate
Compose project, so `docker compose -f infra/docker-compose.yml exec postgres`
answers `service "postgres" is not running` even while QA is up.

```bash
cd infra/qa
docker compose -f docker-compose.qa.yml exec -T postgres \
  psql -U townbasket -d townbasket < ../dev/mock-analytics-data.sql   # analytics panels
docker compose -f docker-compose.qa.yml exec -T postgres \
  psql -U townbasket -d townbasket < ../dev/mock-user-orders.sql      # one customer's order history
```
Both are re-runnable: each clears only its own `mock-%` orders and rebuilds
them, leaving orders placed by testers alone. Use the `DB_USERNAME` from `.env`
if you changed it from the `townbasket` default.

## Connecting a local SQL client to the QA database

QA's postgres publishes NO port — only Caddy binds 80/443, so the database is
reachable on the Compose network and nowhere else. Keep it that way (an exposed
5432 with a password is a standing invitation) and tunnel over SSH instead.

SSH resolves the forward's target from the SERVER side, and the droplet can
route to the container's bridge IP, so no port has to be published anywhere:

```bash
QA=root@qa.town-basket.com   # or the droplet IP

IP=$(ssh $QA "docker inspect -f \
  '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' qa-postgres-1")

ssh -N -L 5433:$IP:5432 $QA        # leave this running
```

Then point the client at `localhost:5433`, database `townbasket`, user
`DB_USERNAME` (default `townbasket`), password `DB_PASSWORD` from `.env`:

```bash
psql "postgresql://townbasket@localhost:5433/townbasket"
```

The container is `qa-postgres-1` — Compose derives the project name from the
`infra/qa` directory. Its bridge IP changes whenever the container is
recreated, so re-run the lookup if an established tunnel starts refusing
connections.

For a one-off query, skip the tunnel:

```bash
ssh $QA "cd Ecomm/infra/qa && docker compose -f docker-compose.qa.yml \
  exec -T postgres psql -U townbasket -d townbasket -c 'select count(*) from orders.orders'"
```

Remember the schema-per-module layout: tables live in `catalog`, `orders`,
`inventory`, … not `public`. `\dn` lists them.

## Real mobile-OTP verification in QA

QA ships with the OFFLINE verifier: any 10-digit phone, OTP token
`dev:<phone>`, no SMS, no cost. To exercise the real Firebase phone-OTP path
instead, both halves must move together — the storefront must request a real SMS
AND the API must verify a Google-signed token. One side alone fails every login.

In the Firebase console first:

1. **Authentication → Sign-in method → Phone** — enable it.
2. **Authentication → Settings → Authorized domains** — add `qa.town-basket.com`.
   Without this, reCAPTCHA refuses to run on the QA host.
3. **Phone → "Phone numbers for testing"** — add each tester's number with a
   fixed 6-digit code. Those numbers verify through the real code path **without
   sending an SMS**, so QA stays free and repeatable. Prefer this over billing
   real SMS to test logins; a QA login loop can burn through quota fast.

Then in `infra/qa/.env`, uncomment the OTP block (one backend var + six
storefront build args — the file spells them out) and rebuild:

```bash
cd infra/qa
docker compose -f docker-compose.qa.yml up -d --build api storefront
```

A rebuild, not a restart: `NEXT_PUBLIC_*` is inlined at build time.

To go back to the fake, **comment the backend line out again** rather than
blanking it. `TOWNBASKET_IDENTITY_FIREBASE_PROJECT_ID` is a pass-through, so an
absent variable is omitted from the container entirely, while a blank one is
refused — blank is never treated as "use the fake", because that would silently
downgrade a real deployment to a verifier accepting any phone number.

QA's basic-auth gate does not interfere: phone auth runs in-page against Google's
own endpoints and needs no same-origin callback handler.

## Smoke test after deploy

- https://qa.town-basket.com → basic auth (`qa` / your password) → storefront
  loads, green theme
- Storefront login: any 10-digit phone with OTP token `dev:<phone>`
- Place a pay-on-delivery order → confirm it appears in qa-admin's queue →
  assign to the delivery agent → confirm in qa-delivery with the order's OTP
- Admin login: `admin@townbasket.local` / `Admin@12345` (QA-only seed)

## What QA must NEVER become

Do not point Razorpay live keys, real customer data, or the production DNS at
this stack. When production launches (see `infra/deploy/`), it runs on its own
droplet with real verifiers and rotated credentials; QA stays the sandbox.
