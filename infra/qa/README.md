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

## Smoke test after deploy

- https://qa.town-basket.com → basic auth (`qa` / your password) → storefront
  loads, green theme
- Storefront login: any 10-digit phone with OTP token `dev:<phone>`
- Place a COD order → confirm it appears in qa-admin's queue → assign to the
  delivery agent → confirm in qa-delivery with the order's OTP
- Admin login: `admin@townbasket.local` / `Admin@12345` (QA-only seed)

## What QA must NEVER become

Do not point Razorpay live keys, real customer data, or the production DNS at
this stack. When production launches (see `infra/deploy/`), it runs on its own
droplet with real verifiers and rotated credentials; QA stays the sandbox.
