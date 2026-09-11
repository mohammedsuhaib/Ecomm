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
   # fill DB_PASSWORD and JWT_SECRET, then let this command append the
   # basic-auth hash CORRECTLY QUOTED (delete the placeholder line first):
   printf "QA_BASIC_AUTH_HASH='%s'\n" \
     "$(docker run --rm caddy:2-alpine caddy hash-password --plaintext 'your-qa-password')" >> .env
   ```
   The single quotes around the hash are load-bearing: unquoted, Compose's
   env-file interpolation expands the hash's `$`-sequences as variables and
   silently corrupts it — basic auth then rejects every password. Letting the
   command above emit the line keeps the quoting out of human hands, and the
   deploy workflow re-verifies the resolved hash before every `up`, failing
   with a clear error if it has been corrupted.
5. **Launch**:
   ```bash
   docker compose -f docker-compose.qa.yml up -d --build
   ```
   First build takes several minutes (Maven + three Next builds). Flyway
   creates and seeds the QA database on API startup.

## Deploying a new version to QA

**Automatic (CD):** `.github/workflows/deploy-qa.yml` redeploys QA when the
`CI` workflow finishes **green** on a `main` commit that touches `apps/`,
`packages/`, or `infra/qa/` (or on manual dispatch from the Actions tab) — a
commit that fails tests or the Modulith boundary check never reaches QA. The
workflow SSHes into the droplet, checks out the exact CI-validated commit,
verifies the basic-auth hash survived `.env` interpolation, runs the compose
build, recreates Caddy (so Caddyfile changes actually apply), and probes each
app upstream from inside the compose network; afterwards it polls `qa-api`'s
`/actuator/health` and confirms the storefront still demands basic auth. If
the build fails, the previous containers keep running — QA stays on the old
version and the workflow goes red. Deploys hard-reset the clone: **edits made
directly on the droplet to tracked files are overwritten** by the next
deploy, so commit hotfixes instead of patching the box. Enable it with two
repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `QA_DROPLET_SSH_KEY` | Private key with SSH access to the QA droplet |
| `QA_DROPLET_HOST` | QA droplet public IP or hostname |
| `QA_DROPLET_USER` (optional) | SSH user, defaults to `root` |
| `QA_DROPLET_REPO_DIR` (optional) | Repo path on the droplet, defaults to `Ecomm` under `$HOME` (absolute paths honored as-is) |
| `QA_DROPLET_HOST_KEY` (optional, recommended) | Output of `ssh-keyscan -t ed25519 <droplet-ip>`; when set, SSH verifies the droplet's host key on every run instead of trusting whatever answers on first contact |

**Existing droplet** (provisioned before the deploy-key instructions above):
its clone's `origin` is the credential-less HTTPS URL, which the workflow
cannot fetch non-interactively — deploys fail fast with a pointer here until
the box is migrated once:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519 -N "" -C "townbasket-qa-droplet"
cat ~/.ssh/id_ed25519.pub          # add as a read-only deploy key on GitHub
ssh-keyscan github.com >> ~/.ssh/known_hosts
cd ~/Ecomm && git remote set-url origin git@github.com:mohammedsuhaib/Ecomm.git
```

Also re-quote `QA_BASIC_AUTH_HASH` in `infra/qa/.env` (see step 4): the
pre-fix instructions said to paste the hash unquoted, which corrupts it —
the deploy now detects a corrupted hash and refuses to proceed until it is
fixed.

**Manual (or to test a feature branch):**
```bash
cd Ecomm && git checkout main && git pull   # or: git checkout <feature-branch>
cd infra/qa && docker compose -f docker-compose.qa.yml up -d --build
```
Only changed images rebuild (Docker layer cache). To wipe QA data and start
fresh: add `down -v` before the `up`. If you park QA on a feature branch,
remember the next merge to `main` will auto-deploy over it.

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
storefront build args — the file spells them out) and rebuild.

**Copy `projectId` verbatim from the console.** Firebase appends a generated
suffix to a taken name, so the project you asked to call `town-basket-qa` may
actually be `town-basket-qa-baf89`. The verifier requires
`iss = https://securetoken.google.com/<projectId>` and `aud = <projectId>` to
match exactly, so a shortened id 401s every login.

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

### When a login returns 401 "Invalid phone token"

That response is deliberately vague — it never says which check failed. The API
log does. Read it first:

```bash
docker compose -f docker-compose.qa.yml logs api | grep -i "Phone-OTP\|Phone token"
```

One `Phone-OTP:` line is written at startup naming the ACTIVE verifier, and every
rejection logs its specific cause. The three failures worth knowing:

| Log says | Cause | Fix |
|---|---|---|
| `OFFLINE dev verifier active` + `looks like a real Firebase ID token` | Storefront rebuilt with the Firebase config, API never got the projectId | Set `TOWNBASKET_IDENTITY_FIREBASE_PROJECT_ID`, then `up -d api` |
| `received a dev: token while the REAL verifier is active` | The reverse — API switched, storefront still built without the args | `up -d --build storefront` |
| `rejected while verifying against projectId '...'` | projectId mismatch (usually the missing generated suffix) | Correct it on BOTH sides, rebuild both |

Confirm what the container actually holds rather than what `.env` says:

```bash
docker compose -f docker-compose.qa.yml exec api env | grep -i firebase
docker compose -f docker-compose.qa.yml exec storefront \
  sh -c 'grep -rlo "<your-project-id>" .next | head -1'
```

An empty first result means the API is on the fake; an empty second means the
storefront image was built without the Firebase args.

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
