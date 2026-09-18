# Logs in Grafana Cloud

A single [Grafana Alloy](https://grafana.com/docs/alloy/) agent ships every
container's logs off the droplet to Grafana Cloud's hosted Loki, where you view
and search them in a browser. The agent stores nothing locally, so the logs
outlive the container — and the droplet.

It is **off by default**. Nothing here runs, and no account is needed, until you
opt in per environment.

## One-time setup

Three values have to travel from Grafana Cloud to the droplet's `.env`, and
that is the whole job. **Do QA first**, then repeat part B for production with
its own token.

> The credentials live in the Grafana Cloud **Portal** (grafana.com), not
> inside your Grafana instance. Grafana moves this UI around — the "Hosted
> logs" tile under *Connections → Add new connection* no longer exists — so
> the navigation below was re-checked against Grafana's docs in September 2026.
> If a menu has moved again, the two things you are hunting for are unchanged:
> the stack's **Loki endpoint + instance id**, and an **access policy token**
> scoped `logs:write`.

### A. Get the three values (browser, ~5 minutes)

1. Create a free [Grafana Cloud](https://grafana.com/auth/sign-up/create-user)
   account if you have none. The free tier is ~50 GB of logs with ~14-day
   retention, far above this shop's volume.
2. Sign in at [grafana.com](https://grafana.com/auth/sign-in/). That lands on
   the **Cloud Portal**, which is a different place from your Grafana stack.
3. Pick your organisation, then your stack in the left-hand overview. Find the
   **Loki** (Logs) tile and press **Details** / **Send Logs**. Copy:
   - **URL** — e.g. `https://logs-prod-012.grafana.net`
   - **User** — a number, e.g. `123456` (the basic-auth username)
4. Add the push path yourself; the tile usually shows only the base URL:
   ```
   https://logs-prod-012.grafana.net/loki/api/v1/push
   ```
5. In the Portal, go to **Security → Access Policies**
   (`grafana.com/orgs/<your-org>/access-policies`) and press **Create access
   policy**. Name it per environment, e.g. `townbasket-qa-logs`; set **Realm**
   to your stack; tick the **`logs:write`** scope and nothing else.
6. Open the policy, press **Add token**, and copy the `glc_…` value. It is
   shown once. One token per environment, so QA can be revoked without
   touching production.

### B. Put them on the droplet (~2 minutes)

7. SSH in and open the stack's `.env` — `~/Ecomm/infra/qa` for QA,
   `~/Ecomm/infra/deploy` for production:
   ```bash
   ssh root@<droplet-ip>
   cd ~/Ecomm/infra/qa
   nano .env
   ```
8. Append the profile line and the three values:
   ```
   COMPOSE_PROFILES=monitoring
   GRAFANA_LOKI_URL=https://logs-prod-012.grafana.net/loki/api/v1/push
   GRAFANA_LOKI_USER=123456
   GRAFANA_LOKI_TOKEN=glc_…
   ```
   The token is a secret — it lives in `.env`, never in git.
9. Start the agent. Only the agent starts; nothing rebuilds and no app
   restarts:
   ```bash
   chmod 600 .env
   docker compose -f docker-compose.qa.yml up -d     # prod: docker-compose.prod.yml
   ```

`.env` is untracked, so a deploy's hard reset of the clone leaves it alone:
every future deploy — including the auto-deploy on green `main` — starts the
agent from then on. Both deploy workflows run a plain `up -d` with no service
list, so that profile line is the only switch.

## C. Checking it actually works

Straight after step 9, and in this order, because each step rules out the one
below it:

1. `docker compose -f docker-compose.qa.yml ps alloy` — the container should be
   `Up`. Missing entirely means the profile is not set in `.env`.
2. `docker compose -f docker-compose.qa.yml logs alloy | tail -30` — look for
   `Alloy is running`. Credential problems show here as 401s from the push
   endpoint; a blank URL shows as a parse or connection error.
3. In Grafana → Explore → Loki, run `{env="qa"}` over the last 15 minutes. If
   the agent is up and the query is empty, the token is usually scoped to the
   wrong stack or missing `logs:write`.
4. Make a line to look for: open `https://qa.town-basket.com` in a browser,
   then query `{env="qa", service="caddy"}` and watch your own request arrive.
   Then try `{env="qa", service="api", level="ERROR"}` to confirm the labels.

What the three usual failures look like:

| Symptom | Cause |
|---|---|
| No `alloy` container at all | `COMPOSE_PROFILES=monitoring` missing from `.env` |
| Agent up, 401s in its log | Wrong `GRAFANA_LOKI_USER`, or a token without `logs:write` |
| Agent up and quiet, Explore empty | Token belongs to a different stack than the URL |

To turn it all off again: delete the `COMPOSE_PROFILES` line and run
`docker compose -f docker-compose.qa.yml up -d --remove-orphans`.

The agent's own diagnostics UI listens on port 12345 inside the container and
is deliberately NOT published to the host. To look at it during an
investigation, tunnel to it (`ssh -L 12345:localhost:12345 …`) rather than
opening the port.

## Upgrading the agent

The image is pinned (`grafana/alloy:${ALLOY_VERSION:-v1.19.2}`) because this
thing runs unattended: with `latest`, a silent major upgrade can break log
shipping on the day nobody is looking at Grafana. To bump it, set
`ALLOY_VERSION` in `.env`, redeploy, and check step 2 above — or raise the
default in both compose files once it has proved itself in QA.

## Viewing the logs

Grafana Cloud → **Explore** → pick your Loki data source, then query with LogQL:

| Want | Query |
|---|---|
| Everything from QA | `{env="qa"}` |
| Just the API | `{service="api"}` |
| API errors on prod | `{env="prod", service="api", level="ERROR"}` |
| Warnings and errors together | `{service="api"} \| level=~"WARN\|ERROR"` |
| A specific phone-OTP rejection | `{service="api"} \|= "Phone-OTP"` |
| A password reset audit trail | `{service="api"} \|= "Password reset for user"` |
| What Caddy served | `{service="caddy"} \| json \| status >= 400` |

Labels are kept deliberately low-cardinality: `env` (qa/prod), `service` (the
Compose service name), `container`, and `level` on the API only. Everything
else is in the log line, so grep it with LogQL's `|=` / `|~` rather than adding
labels — a label per order id or per URL path is how a Loki bill explodes.

`level` is lifted out of the API's log line by the agent, so
`{service="api", level="ERROR"}` is exact where `|= "ERROR"` also matches the
word inside a message. The other services keep their own shapes untouched:
Caddy's one-JSON-object-per-line survives intact, which is why `| json` works
on it.

### Stack traces arrive one line per entry

A Java exception is one event that Docker hands over as forty lines, and each
becomes its own Loki entry. Use Grafana's **show context** on the ERROR line to
read the frames around it.

This is not for want of trying: Alloy's `stage.multiline` does not join lines
coming from `loki.source.docker`. It was tested against a real Loki, both
nested in the API's `stage.match` and hoisted into its own pipeline, and joined
nothing either way — so rather than leave a rule in the config that looks like
it works, there is a comment there saying it does not. The real fix belongs in
the application: one line per event, i.e. structured JSON logging, which needs
either a Spring Boot 3.4+ upgrade (`logging.structured.format.console`) or the
logstash encoder on 3.3.

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
