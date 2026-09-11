# Production deployment — DigitalOcean droplet

Town Basket runs on a single DigitalOcean droplet (Bangalore region) under
Docker Compose, with Caddy fronting three subdomains with automatic TLS
(ARCHITECTURE.md §2, §7, §8). This directory holds the production deployment
artifacts. Full provisioning/runbook polish lands in **M6**.

## Files

| File | Purpose |
|---|---|
| `docker-compose.prod.yml` | Production compose: Caddy + api + storefront + admin + delivery (+ optional Postgres). Pulls pre-built images from the registry. |
| `Caddyfile` | Reverse proxy + auto-TLS for `shop.`, `admin.`, `api.`, `delivery.town-basket.com`. |
| `backup/nightly-backup.sh` | `pg_dump` → gzip → upload to DO Spaces; retention pruning. Run nightly via cron/systemd timer. |

## Topology

```
Internet ──▶ Caddy (:80/:443, auto-TLS)
               ├── shop.town-basket.com     ──▶ storefront:3000
               ├── admin.town-basket.com    ──▶ admin:3001
               ├── api.town-basket.com      ──▶ api:8080
               └── delivery.town-basket.com ──▶ delivery:3002
```

Postgres is **recommended on DO Managed Database** (durability, PITR, patching
handled by the provider). A containerized `postgres` service is included in the
prod compose as a fallback, commented, for cost-sensitive single-box setups.

## Deploy flow (CD)

Deploys are **automatic** — `.github/workflows/deploy-app.yml`:

1. Merge to `main` → the `CI` workflow runs (all tests + Modulith boundary check).
2. When CI is green, **Deploy app** builds the four prod images (api,
   storefront, admin, delivery) and pushes them to GHCR
   (`ghcr.io/<owner>/town-basket-*`), tagged `latest` **and** `sha-<commit>`.
   The `NEXT_PUBLIC_*` browser config is baked in as build args at this step
   (Next.js inlines it at build time — runtime env only affects SSR).
3. It then SSHes to the droplet, resets the repo checkout to `origin/main`
   (source of the compose file + Caddyfile), logs Docker into GHCR with a
   job-scoped token (no long-lived registry credential stays on the droplet),
   and runs `docker compose -f docker-compose.prod.yml pull && … up -d`
   pinned to the exact `sha-<commit>` tag (pull-and-restart; brief blip,
   acceptable at ~100 orders/day — no multi-instance zero-downtime).
4. Post-deploy health checks hit all four subdomains against the droplet IP
   (API via `/actuator/health`), with retries while containers start.

Doc-only merges (README, `holding-site/` — which has its own workflow —,
`brand/`) skip the deploy. The workflow can also be run manually from the
Actions tab (`workflow_dispatch`).

**Rollback:** on the droplet, re-run compose pinned to a previous commit's tag:

```bash
cd ~/Ecomm/infra/deploy
REGISTRY=ghcr.io/<owner> TAG=sha-<old-commit> docker compose -f docker-compose.prod.yml up -d
```

### GitHub Actions secrets (repo Settings → Secrets and variables → Actions)

Same secrets the holding-site deploy already uses: `DROPLET_SSH_KEY`,
`DROPLET_HOST`; optional `DROPLET_USER` (default `root`) and
`DROPLET_REPO_DIR` (default `Ecomm`, relative to `$HOME`). The workflow fails
fast with a clear message when they are missing. Optional repo **variables**
`NEXT_PUBLIC_FIREBASE_*` / `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY` override the
public frontend build config (defaults match `.env.example`).

### One-time droplet setup

1. Docker + the compose plugin installed; the repo cloned at `$HOME/Ecomm`.
2. `cp .env.example .env` in `infra/deploy/`, fill in real values, `chmod 600 .env`.
3. DNS for the four subdomains pointing at the droplet (Caddy handles TLS).

The deploy fails with a clear error if `.env` is missing on the droplet.

## Secrets

Provided via a root-restricted `.env` next to the prod compose (never in the
repo): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, DO Spaces keys, Paytm +
Firebase credentials. See `.env.example` for the expected keys. Registry
pull credentials are *not* kept on the droplet — CD logs in with a job-scoped
token on each deploy.

## Durability

Nightly off-site backups to DO Spaces with a tested restore runbook are the
priority over failover (a single droplet has no auto-failover — a conscious
trade-off, see §7/§7a). Alert on backup success/failure.
