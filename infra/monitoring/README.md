# Logs in Grafana Cloud

A single [Grafana Alloy](https://grafana.com/docs/alloy/) agent ships every
container's logs off the droplet to Grafana Cloud's hosted Loki, where you view
and search them in a browser. The agent stores nothing locally, so the logs
outlive the container — and the droplet.

It is **off by default**. Nothing here runs, and no account is needed, until you
opt in per environment.

## One-time setup

1. Create a free [Grafana Cloud](https://grafana.com/auth/sign-up/create-user)
   account (the free tier is ~50 GB of logs with ~14-day retention — far above
   this app's volume).
2. In Grafana Cloud → **Connections → Add new connection → Hosted logs (Loki)**,
   generate an access policy / token. You get three values:
   - **URL** — the push endpoint, e.g. `https://logs-prod-012.grafana.net/loki/api/v1/push`
   - **User** — a numeric instance id (the basic-auth username)
   - **Token** — an API key scoped `logs:write` (the basic-auth password)
3. On the droplet, in the stack's `.env` (`infra/qa/.env` or `infra/deploy/.env`):
   ```
   COMPOSE_PROFILES=monitoring
   GRAFANA_LOKI_URL=…/loki/api/v1/push
   GRAFANA_LOKI_USER=123456
   GRAFANA_LOKI_TOKEN=glc_…
   ```
   The token is a secret — it lives in `.env`, never in git.
4. Redeploy: `docker compose -f docker-compose.qa.yml up -d` (or the prod file).
   Because `COMPOSE_PROFILES=monitoring` is in `.env`, every future deploy —
   including the auto-deploy on green `main` — starts the agent automatically.

## Viewing the logs

Grafana Cloud → **Explore** → pick your Loki data source, then query with LogQL:

| Want | Query |
|---|---|
| Everything from QA | `{env="qa"}` |
| Just the API | `{service="api"}` |
| API errors on prod | `{env="prod", service="api"} \|= "ERROR"` |
| A specific phone-OTP rejection | `{service="api"} \|= "Phone-OTP"` |
| A password reset audit trail | `{service="api"} \|= "Password reset for user"` |

Labels are kept deliberately low-cardinality: `env` (qa/prod), `service` (the
Compose service name), and `container`. Everything else is in the log line, so
grep it with LogQL's `|=` / `|~` rather than adding labels.

## What it does NOT replace

This is log viewing and search only. It is one of four separate concerns:

- **Logs** — here (Grafana Cloud / Loki).
- **Infra metrics + alerts** (CPU, memory, disk, droplet-down) — DigitalOcean's
  own graphs and Alert Policies.
- **Backup ran or not** — a Healthchecks.io dead-man's-switch on the nightly
  dump (a full DB backup you never verify is not a backup).
- **Application errors with stack traces** — Sentry or GlitchTip.

## Turning it off

Remove (or comment) `COMPOSE_PROFILES=monitoring` from `.env`, then
`docker compose … up -d --remove-orphans` to stop and remove the agent.
