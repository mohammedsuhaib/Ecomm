# Production deployment — DigitalOcean droplet

Town Basket runs on a single DigitalOcean droplet (Bangalore region) under
Docker Compose, with Caddy fronting three subdomains with automatic TLS
(ARCHITECTURE.md §2, §7, §8). This directory holds the production deployment
artifacts. Full provisioning/runbook polish lands in **M6**.

## Files

| File | Purpose |
|---|---|
| `docker-compose.prod.yml` | Production compose: Caddy + api + storefront + admin (+ optional Postgres). Pulls pre-built images from the registry. |
| `Caddyfile` | Reverse proxy + auto-TLS for `shop.`, `admin.`, `api.town-basket.com`. |
| `backup/nightly-backup.sh` | `pg_dump` → gzip → upload to DO Spaces; retention pruning. Run nightly via cron/systemd timer. |

## Topology

```
Internet ──▶ Caddy (:80/:443, auto-TLS)
               ├── shop.town-basket.com   ──▶ storefront:3000
               ├── admin.town-basket.com  ──▶ admin:3001
               └── api.town-basket.com    ──▶ api:8080
```

Postgres is **recommended on DO Managed Database** (durability, PITR, patching
handled by the provider). A containerized `postgres` service is included in the
prod compose as a fallback, commented, for cost-sensitive single-box setups.

## Deploy flow (CD)

1. Merge to `main` → CI builds images → pushes to the registry.
2. On the droplet: `docker compose -f docker-compose.prod.yml pull && \
   docker compose -f docker-compose.prod.yml up -d` (pull-and-restart; brief
   blip, acceptable at ~100 orders/day — no multi-instance zero-downtime).

## Secrets

Provided via a root-restricted `.env` next to the prod compose (never in the
repo): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, registry creds, DO Spaces keys,
Paytm + Firebase credentials. See `.env.example` for the expected keys.

## Durability

Nightly off-site backups to DO Spaces with a tested restore runbook are the
priority over failover (a single droplet has no auto-failover — a conscious
trade-off, see §7/§7a). Alert on backup success/failure.
