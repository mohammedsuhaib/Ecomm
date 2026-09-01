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

1. **Droplet**: a $6–12/mo DigitalOcean droplet (Bangalore). Image: the
   Marketplace tab's "Docker" 1-Click app — or plain Ubuntu 24.04 plus
   `curl -fsSL https://get.docker.com | sh` (installs the engine and the
   Compose v2 plugin). The from-source build needs ~2 GB RAM; on the
   smallest droplet add swap first:
   ```bash
   fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
   ```
2. **DNS** — four A records → the QA droplet IP:
   `qa`, `qa-admin`, `qa-api`, `qa-delivery` (all under town-basket.com).
3. **Firewall**: `ufw allow 80 && ufw allow 443 && ufw allow OpenSSH && ufw --force enable`
4. **Clone + secrets** — the repo is private, so give the droplet a
   **read-only deploy key** first (GitHub → Ecomm → Settings → Deploy keys →
   Add, "Allow write access" unchecked). The SSH remote means the deploy
   workflow's later `git fetch` reuses the same key:
   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519 -N "" -C "townbasket-qa-droplet"
   cat ~/.ssh/id_ed25519.pub          # paste this as the deploy key
   ssh-keyscan github.com >> ~/.ssh/known_hosts
   git clone git@github.com:mohammedsuhaib/Ecomm.git && cd Ecomm/infra/qa
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

**Automatic (CD):** `.github/workflows/deploy-qa.yml` redeploys QA on every
merge to `main` that touches `apps/`, `packages/`, or `infra/qa/` (and can be
run manually from the Actions tab). It SSHes into the droplet, resets the
clone to `origin/main`, and runs the compose build; afterwards it polls
`qa-api`'s `/actuator/health` and confirms the storefront still demands basic
auth. If the build fails, the previous containers keep running — QA stays on
the old version and the workflow goes red. Enable it with two repository
secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `QA_DROPLET_SSH_KEY` | Private key with SSH access to the QA droplet |
| `QA_DROPLET_HOST` | QA droplet public IP or hostname |
| `QA_DROPLET_USER` (optional) | SSH user, defaults to `root` |
| `QA_DROPLET_REPO_DIR` (optional) | Repo path on the droplet, defaults to `Ecomm` in `$HOME` |

Note the workflow deploys whatever is on `main`, whether or not CI has
finished — a broken build simply leaves QA on the previous version.

**Manual (or to test a feature branch):**
```bash
cd Ecomm && git checkout main && git pull   # or: git checkout <feature-branch>
cd infra/qa && docker compose -f docker-compose.qa.yml up -d --build
```
Only changed images rebuild (Docker layer cache). To wipe QA data and start
fresh: add `down -v` before the `up`. If you park QA on a feature branch,
remember the next merge to `main` will auto-deploy over it.

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
