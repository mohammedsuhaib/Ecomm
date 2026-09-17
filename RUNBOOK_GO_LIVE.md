# Go-live runbook — step by step

Executable companion to [`GO_LIVE.md`](./GO_LIVE.md), which explains *why* each
item matters. This one is *how*. Stages are ordered; within a stage, steps are
ordered. Every code step says which file to touch and how to verify it.

Nothing here is applied yet.

---

## Is the production database a separate instance?

**By design, yes — a separate managed instance, not a container on the droplet.**
`infra/deploy/.env.example` is explicit:

```
# Database (recommended: DO Managed Postgres)
DB_URL=jdbc:postgresql://db-host:25060/townbasket?sslmode=require
```

Port `25060` and `sslmode=require` are DigitalOcean Managed Postgres
conventions, so the intended topology is: six containers on the droplet, the
database on a managed instance reached over TLS. `docker-compose.prod.yml`
hard-requires `DB_URL` with `${DB_URL:?…}` and carries a **commented-out**
`postgres` service as a documented fallback "only for a cost-sensitive
single-box deployment," with a note to re-cut the memory budget if you enable
it. `ARCHITECTURE.md` §7 gives the reasoning: managed Postgres means backups,
patching and PITR are the provider's problem, which matters when the
availability plan is "no failover, protect the data."

Local dev is different and always self-contained: `infra/docker-compose.yml`
runs `postgres:16-alpine` as a service with
`DB_URL: jdbc:postgresql://postgres:5432/townbasket`.

**But the repo cannot tell you what the live droplet actually uses** — `DB_URL`
lives in the droplet's `.env`, which is deliberately never committed. Verify
before you touch anything; Step 0.1 shows how. It changes the backup story
(managed = provider PITR *plus* your nightly dump; container = your dump is the
only copy) and it changes how you open a psql session in Step 1.9.

---

## Stage 0 — Preflight

Do all of Stage 0 before changing a line. It takes 20 minutes and it is what
makes the rest safe.

### 0.1 Confirm the database topology and reachability

SSH to the droplet, then:

```bash
cd ~/Ecomm/infra/deploy

# Which DB is configured? (prints the host, not the password)
grep -E '^DB_URL=' .env | sed -E 's#(//[^:]+):[^@]*@#\1:***@#'

# Is a postgres container running on the droplet at all?
docker compose -f docker-compose.prod.yml ps
```

- A host like `*.db.ondigitalocean.com` on port `25060` → **managed instance**.
  Confirm PITR/backups are on in the DO console, and note the retention window.
- `postgres:5432` with a `postgres` container in `ps` → **self-hosted on the
  droplet**. Your nightly dump is the only copy of the data, which makes Stage 3
  urgent rather than routine.

Record which it is. Every later step that says "connect to the database" means
this instance.

### 0.2 Take a backup and prove it restores

Do not skip the restore. An untested dump is not a backup.

```bash
cd ~/Ecomm/infra/deploy
set -a; . ./.env; set +a          # load DB_URL_PG, SPACES_*

pg_dump "$DB_URL_PG" | gzip > /tmp/pre-golive-$(date -u +%Y%m%dT%H%M%SZ).sql.gz
ls -lh /tmp/pre-golive-*.sql.gz   # sanity: not a few hundred bytes
```

Restore it into a scratch database and check it has real rows:

```bash
createdb -h <db-host> -p <port> -U "$DB_USERNAME" tb_restore_test
gunzip -c /tmp/pre-golive-*.sql.gz | psql -h <db-host> -p <port> -U "$DB_USERNAME" -d tb_restore_test

psql ... -d tb_restore_test -c "SELECT count(*) FROM orders.orders;"
psql ... -d tb_restore_test -c "SELECT count(*) FROM catalog.products;"
```

Write down the elapsed time — that number is your recovery-time expectation, and
Stage 3.3 turns this into a permanent runbook. Then
`dropdb … tb_restore_test`.

### 0.3 Decide the demo-data strategy by counting real orders

This decides Stage 2.1, so answer it now:

```sql
SELECT count(*) AS total,
       count(*) FILTER (WHERE placed_at > now() - interval '30 days') AS recent
FROM orders.orders;

SELECT count(*) FROM identity.users WHERE role = 'CUSTOMER';
```

- **Zero or only test orders** → clean wipe of the demo catalogue (Stage 2.1,
  path A). Simplest outcome.
- **Real customer orders exist** → do not wipe; correct in place (path B).
  `orders.order_items` snapshots `product_name`, `label` and `unit_price`, so
  order history survives a catalogue delete — but stock and reporting get
  messier, and there is no upside to the risk.

### 0.4 Check who can currently log in with the seeded credentials

This is the finding you are about to fix; confirm its live state so you can
prove the fix worked.

```sql
SELECT id, role, email, active, (password_hash IS NOT NULL) AS has_password
FROM identity.users
WHERE email LIKE '%@townbasket.local';
```

Any row with `active = true` and `has_password = true` is a working login with a
password published in this repository. Expect three.

### 0.5 Confirm QA is deployable

Stage 1 ships through QA first. Verify `deploy-qa.yml` runs green and the QA
environment is reachable before you need it under time pressure.

---

## Stage 1 — Close the three live holes

One focused change set: a migration, a config change, a Caddy tweak and a boot
guard. Branch off `main`.

```bash
git fetch origin main && git checkout -b fix/prod-hardening origin/main
```

### 1.1 De-credential the seeded accounts (A1, part 1)

Create `apps/api/src/main/resources/db/migration/identity/V2_7__retire_dev_seed_accounts.sql`:

```sql
-- Retire the dev seed accounts (V2_2, V2_3, V2_4) in EVERY environment.
--
-- Those migrations create ADMIN / STORE_STAFF / DELIVERY_AGENT logins whose
-- plaintext passwords are written in their own comments, and nothing gates
-- them: flyway.locations lists this folder unconditionally and no profile is
-- set in production. So they are live logins wherever the app has ever run.
--
-- This is a FORWARD migration, not an edit to V2_2/V2_3: editing an applied
-- migration changes its checksum and fails Flyway validation at boot, exactly
-- as the V2_4 comment explains.
--
-- DEACTIVATE rather than DELETE. identity.addresses and identity.refresh_tokens
-- carry real FKs to identity.users, and orders.orders holds user_id as a loose
-- cross-module reference, so a delete either fails or orphans history. Clearing
-- password_hash removes the credential; active = FALSE removes the login.
--
-- On a FRESH database this still lands safely: Flyway applies a migration set
-- in version order, so 2.2 -> 2.3 -> 2.4 -> 2.7 means the accounts are created
-- and then immediately retired. (out-of-order only permits applying a lower
-- version AFTER a higher one is already applied; it does not reorder a batch.)
--
-- Real staff accounts are created through /api/v1/admin/staff, with passwords
-- set by the people who will use them.

UPDATE identity.users
   SET active        = FALSE,
       password_hash = NULL,
       updated_at    = now()
 WHERE email IN ('admin@townbasket.local',
                 'staff@townbasket.local',
                 'delivery@townbasket.local');

-- Any refresh token already issued to these accounts stays valid for up to 30
-- days otherwise, which would outlive the fix.
UPDATE identity.refresh_tokens rt
   SET revoked = TRUE
  FROM identity.users u
 WHERE rt.user_id = u.id
   AND u.email IN ('admin@townbasket.local',
                   'staff@townbasket.local',
                   'delivery@townbasket.local');
```

**Why this is sufficient — verified, not assumed.**
`AuthServiceImpl.staffLogin` (line 122) rejects a user whose `password_hash` is
null *before* it reaches the encoder, so nulling the hash yields a clean `401`
rather than an exception. The `active = false` check at line 136 is a second
gate (a truthful `403` when the password is right but the account is off), and
`refreshToken` independently re-checks `isActive()` at line 169 — so an
already-issued refresh token cannot ride past the fix either. Revoking the
tokens above is belt-and-braces for the 30-day window.

Note the ordering consequence for verification: because the null-hash check
comes first, these accounts return **401**, not 403.

### 1.2 Stop future fixtures reaching production (A1, part 2)

**Do not move `V2_2`, `V2_3`, `V2_4`, `V3_2`, `V4_2` or `V8_2` out of
`db/migration/`.** They are recorded as applied in the `flyway` history schema.
Removing them from the scanned locations makes Flyway report *"Detected applied
migration not resolved locally"* and **fail the boot** of every existing
environment. Working around that needs `ignore-migration-patterns: "*:missing"`,
which would also silence a genuinely missing migration — a worse trade.

Instead, leave history alone and give *new* fixtures a home that production
never scans.

1. Create the directory with a README stating the rule:

   `apps/api/src/main/resources/db/seed/dev/README.md`
   > Dev/QA-only seed data. This location is added to `flyway.locations` **only**
   > under the `local` profile. Production never scans it. Put demo catalogues,
   > test accounts and sample orders here — never in `db/migration/`.

2. In `apps/api/src/main/resources/application.yml`, leave the existing
   `flyway.locations` list exactly as it is and append a profile block at the
   end of the file:

```yaml
---
# Dev/QA-only Flyway location. Fixtures here (demo accounts, sample catalogue)
# must NEVER be reachable in production, so they live outside db/migration and
# are added only under this profile. The historical seeds in db/migration
# (V2_2, V3_2, V4_2, V8_2) stay where they are — their checksums are recorded
# in applied history, and removing them from the scan would fail validation on
# every existing database.
spring:
  config:
    activate:
      on-profile: local
  flyway:
    locations:
      - classpath:db/migration/shared
      - classpath:db/migration/identity
      - classpath:db/migration/catalog
      - classpath:db/migration/inventory
      - classpath:db/migration/cart
      - classpath:db/migration/orders
      - classpath:db/migration/payments
      - classpath:db/migration/serviceability
      - classpath:db/migration/notifications
      - classpath:db/seed/dev
```

   Spring replaces list properties rather than merging them, so the profile
   block must restate the nine base locations. That duplication is the cost of
   not weakening validation; the comment explains why to the next reader.

3. Activate the profile for local dev only — in `infra/docker-compose.yml`,
   on the `api` service:

```yaml
      SPRING_PROFILES_ACTIVE: local
```

   Leave `docker-compose.prod.yml` with no profile. Its absence is the
   production safeguard, so say so in a comment next to the `api` service's
   environment block.

4. Optionally, re-add dev logins under `db/seed/dev/` (e.g.
   `R__dev_accounts.sql` as a repeatable migration) so local development keeps
   working after 1.1 retires the old ones. Keep the documented passwords; they
   can never reach production now.

### 1.3 Close Swagger and the API spec (A5)

Secure by default, matching the pattern the codebase already uses for the
forwarded-for and Firebase settings. Nothing in `src/test` or `ci.yml`
references `/v3/api-docs` or `/swagger-ui`, so disabling by default breaks no
test.

In `application.yml`, add a top-level block:

```yaml
# API docs are DEV/QA tooling, not a production surface. SecurityConfig ends in
# anyRequest().permitAll() and Caddy proxies api.<domain> to the container
# wholesale, so leaving these on publishes every endpoint, parameter and DTO
# shape on the public internet. SECURE DEFAULT: off — enable per environment
# with SPRINGDOC_ENABLED=true (infra/docker-compose.yml sets it for local dev).
springdoc:
  api-docs:
    enabled: ${SPRINGDOC_ENABLED:false}
  swagger-ui:
    enabled: ${SPRINGDOC_ENABLED:false}
```

Then set `SPRINGDOC_ENABLED: "true"` on the `api` service in
`infra/docker-compose.yml`, and in the QA deployment if you want the spec there.
Leave it unset in `docker-compose.prod.yml`.

`OpenApiConfiguration` can stay — it only contributes title/description metadata
and costs nothing when the endpoints are off.

### 1.4 Stop exposing metrics (A6)

In `application.yml`, change the exposure list:

```yaml
management:
  endpoints:
    web:
      exposure:
        # `metrics` removed deliberately. Actuator sits behind the same
        # permitAll catch-all as everything else and Caddy proxies the API host
        # wholesale, so exposing it published http.server.requests counters —
        # per-endpoint request volumes, from which order throughput is directly
        # readable. That is the same disclosure V6_8 removed by replacing
        # sequential order ids with random public codes.
        include: health,info
```

**Why not `management.server.port: 8081`** — the cleaner long-term fix — **not
yet:** `management.server.port` moves *all* actuator endpoints, and
`.github/workflows/deploy-app.yml:291` health-checks
`https://api.town-basket.com/actuator/health` from the GitHub runner. Move the
port and every deploy fails its own verification. If you do move it later, that
check has to become an in-container probe over SSH (`docker compose exec`)
rather than a public HTTPS request. One line now, no blast radius; revisit with
the health check in scope.

**Defence in depth at the proxy** (optional, and test in QA first — a Caddyfile
mistake takes the whole site down). In `infra/deploy/Caddyfile`, replace the
`api.town-basket.com` block:

```
api.town-basket.com {
	encode gzip zstd

	# `route` preserves the order written here; `handle` would be reordered by
	# path specificity.
	route {
		# The deploy workflow's health check needs this one publicly.
		reverse_proxy /actuator/health* api:8080

		# Everything else under /actuator is internal-only, so a future change
		# to management.endpoints…include cannot re-expose it by accident.
		respond /actuator/* 404

		# Normal API traffic. SSE (live order tracking + admin queue) needs
		# unbuffered, long-lived responses.
		reverse_proxy api:8080 {
			flush_interval -1
		}
	}
}
```

⚠️ **A Caddyfile change does not take effect on deploy.** The workflow does
`git reset --hard origin/main` (updating the file on disk) then `compose pull &&
up -d` — but the Caddyfile is a read-only bind mount and the `caddy` service
spec is unchanged, so Compose does not recreate the container and the new config
is never loaded. Either add a reload step to `deploy-app.yml` after `up -d`:

```bash
docker compose -f docker-compose.prod.yml exec -T caddy \
  caddy reload --config /etc/caddy/Caddyfile
```

or run it by hand after this deploy. Adding it to the workflow is better — it is
idempotent, and the next person to edit the Caddyfile will not know this.

### 1.5 Make a fake payment provider unable to reach production (A4)

Two changes, mirroring how `FirebaseNotConfiguredCondition` gates the fake phone
verifier.

**a) Stop `FakeProvider` being a bean outside dev/test.** In
`payments/internal/FakeProvider.java`, add a condition:

```java
@Component
@Primary
@ConditionalOnProperty(name = "townbasket.payments.fake-upi-enabled", havingValue = "true")
class FakeProvider implements PaymentProvider {
```

Then in `application.yml`, under `townbasket.payments`:

```yaml
    # The fake UPI provider auto-succeeds (returns PAID with a synthetic
    # reference), so it must not be a bean in production. SECURE DEFAULT: off.
    # infra/docker-compose.yml turns it on for local dev; tests set it via
    # their own properties.
    fake-upi-enabled: ${FAKE_UPI_ENABLED:false}
```

Set `FAKE_UPI_ENABLED: "true"` on the `api` service in
`infra/docker-compose.yml`, and add the property to whatever test configuration
exercises a UPI checkout (grep `src/test` for `UPI` to find them). Leave it
unset in prod.

**b) Fail the boot on a dangerous combination.** In
`payments/internal/PaymentServiceImpl.java`, after the provider map is built:

```java
        // Fail fast rather than accept money that never arrives. `upi-enabled`
        // is otherwise the ONLY thing between a fake provider and orders marked
        // PAID with nothing received — a one-word change in .env by someone who
        // reads it as a feature toggle. Same posture as PhoneVerifierMode: name
        // the property and the fix, at startup, not at checkout.
        if (properties.upiEnabled()) {
            PaymentProvider upi = providers.get(PaymentMethod.UPI);
            if (upi == null || upi.getClass().getSimpleName().startsWith("Fake")) {
                throw new IllegalStateException(
                        "townbasket.payments.upi-enabled is true but no real UPI provider is "
                        + "registered (found: " + (upi == null ? "none" : upi.getClass().getSimpleName())
                        + "). Integrate a live gateway behind PaymentProvider, or set "
                        + "UPI_ENABLED=false.");
            }
        }
```

**While you are here — a latent trap for whoever integrates the real gateway.**
The constructor takes `List<PaymentProvider>` and uses `putIfAbsent`, and the
comment says "the @Primary FakeProvider wins." `@Primary` governs single-bean
injection, **not** the order of an injected `List` — that is decided by
`@Order`/`Ordered`, falling back to bean-definition order. With one UPI provider
it works either way, so nothing is broken today. But add a second and which one
wins is effectively undefined. Change (a) removes the hazard entirely by making
`FakeProvider` absent in prod; note it in the constructor comment so the next
person does not reintroduce it.

### 1.6 Tests

Add these to the existing suite before deploying:

1. **Seed accounts cannot log in.** An integration test (extend
   `AbstractIntegrationTest`) that POSTs `/api/v1/auth/staff/login` with each of
   the three `@townbasket.local` addresses and its published password, asserting
   401. This is the regression guard that stops a future seed reopening A1.
2. **Boot guard fires.** A context test asserting startup fails when
   `townbasket.payments.upi-enabled=true` and `fake-upi-enabled=false`.
3. **Fake provider absent by default.** Assert no `PaymentProvider` bean for
   `UPI` exists with default properties.
4. **Actuator surface.** Assert `/actuator/metrics` returns 404 and
   `/actuator/health` returns 200.
5. **Docs off by default.** Assert `/v3/api-docs` is 404 with default properties.

Run the full build — including the Modulith boundary check, which will complain
if anything landed in the wrong module:

```bash
cd apps/api
./mvnw -B -ntp verify
```

### 1.7 Verify locally before pushing

```bash
docker compose -f infra/docker-compose.yml up --build
```

Then, against `localhost:8080`:

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/health   # 200
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/metrics  # 404
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/v3/api-docs       # 200 locally (SPRINGDOC_ENABLED=true)
```

Confirm local dev still works end to end: the dev seed accounts you re-added in
1.2.4 can log in, and a UPI checkout still succeeds locally (fake provider on).
If local dev breaks, the profile wiring in 1.2 is wrong — fix it before
deploying, because the production path is the *untested* half of that change.

### 1.8 Ship it

1. Push the branch, open a PR, let CI go green (`./mvnw verify` + frontend
   type-check and build).
2. **Deploy to QA first** (`deploy-qa.yml`). On QA, verify:
   `/actuator/metrics` → 404, `/v3/api-docs` → 404 (unless you enabled it
   there), seeded accounts rejected, a COD checkout still completes end to end.
   If you changed the Caddyfile, confirm the QA site is still fully reachable.
3. Merge to `main`. `deploy-app.yml` builds, pushes, deploys the pinned
   `sha-<commit>` tag, and health-checks all four subdomains.
4. Watch the API container's logs through the boot. Flyway applies `V2_7` here;
   a failure at this step is most likely the checksum/validation issue 1.2
   warns about.

```bash
docker compose -f docker-compose.prod.yml logs -f api | head -100
```

### 1.9 Post-deploy verification (do not skip)

From your own machine:

```bash
curl -s -o /dev/null -w 'health   %{http_code}\n' https://api.town-basket.com/actuator/health
curl -s -o /dev/null -w 'metrics  %{http_code}\n' https://api.town-basket.com/actuator/metrics
curl -s -o /dev/null -w 'apidocs  %{http_code}\n' https://api.town-basket.com/v3/api-docs
curl -s -o /dev/null -w 'swagger  %{http_code}\n' https://api.town-basket.com/swagger-ui/index.html
```

Expect `200`, then `404`, `404`, `404`.

Then prove the credentials are dead — the whole point of the change set:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  https://api.town-basket.com/api/v1/auth/staff/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@townbasket.local","password":"Admin@12345"}'
```

Expect `401`. Repeat for the staff and delivery accounts. Then confirm in the
database (connect to the instance identified in 0.1):

```sql
SELECT email, active, (password_hash IS NOT NULL) AS has_password
FROM identity.users WHERE email LIKE '%@townbasket.local';
-- expect active = false, has_password = false, for all three
```

### 1.10 Create the real staff accounts

Only now, because until 1.9 passes you would be adding accounts to a system that
still has a published admin login.

**There is no API to create an ADMIN or STORE_STAFF account.**
`AdminDirectoryController` exposes `GET /api/v1/admin/staff` (list),
`POST /api/v1/admin/delivery-agents` (riders only) and
`POST /api/v1/admin/users/{id}/password` (reset an *existing* account) — nothing
that creates a manager. Combined with 1.1 retiring the only ADMIN, the first
real admin **must** be inserted directly into the database. This is not a
preference; it is the only path.

Generate a BCrypt hash at cost 10 (matching the seeds) — any BCrypt tool, or a
one-liner against the running container's classpath — then:

```sql
INSERT INTO identity.users (role, email, password_hash, name, active)
VALUES ('ADMIN', '<real-email>', '<bcrypt-hash>', '<Full Name>', TRUE);
```

Verify the login works, then use `POST /api/v1/admin/users/{id}/password` so the
owner sets their own password (an ADMIN may reset staff and riders), and
`ChangePasswordRequest` on `/api/v1/me` for self-service afterwards. Create
riders through `POST /api/v1/admin/delivery-agents`. Additional STORE_STAFF
accounts need the same direct insert with `role = 'STORE_STAFF'`.

Then re-run the 1.9 SQL check to confirm the `.local` accounts are still
retired.

> **Worth fixing soon, not a launch blocker.** "Add a staff member" has no
> supported path, so every future hire is a manual `INSERT` on the production
> database by someone with psql access. A `POST /api/v1/admin/staff` endpoint
> mirroring the existing delivery-agent creation would close it. Track it
> separately — it does not belong in this change set.

### Stage 1 rollback

Re-run compose on the droplet pinned to the previous commit's tag:

```bash
cd ~/Ecomm/infra/deploy
REGISTRY=ghcr.io/mohammedsuhaib TAG=sha-<previous-commit> \
  docker compose -f docker-compose.prod.yml up -d
```

**This does not roll back `V2_7`** — Flyway is forward-only here, and the
accounts stay retired. That is the desired direction anyway; the config changes
are what an image rollback reverts. If you also reverted the Caddyfile, reload
Caddy explicitly (see 1.4).

---

## Stage 2 — Data and configuration

Mostly operator work through the admin UI. No deploy needed except where noted.

### 2.1 Replace the demo catalogue (A2)

Use the strategy chosen in 0.3.

**Path A — clean wipe** (no real orders). Delete children before parents;
`product_variants` has a real FK to `products`, while `inventory.stock_levels`
and `orders.order_items` reference variants by plain id with no FK (by design,
and the order code already tolerates a deleted variant). In one transaction:

```sql
BEGIN;
-- Scope this to the seeded rows only if staff have already added real ones.
DELETE FROM inventory.stock_movements
 WHERE variant_id IN (SELECT id FROM catalog.product_variants);
DELETE FROM inventory.stock_levels
 WHERE variant_id IN (SELECT id FROM catalog.product_variants);
DELETE FROM catalog.product_variants;
DELETE FROM catalog.products;
DELETE FROM catalog.categories;
COMMIT;
```

Take a dump first (0.2), and run it as SQL rather than as a migration — this is
environment-specific data cleanup, not a schema change, and it must not re-run
on QA or a restored copy.

**Path B — correct in place** (real orders exist). Do not delete. Through the
admin UI: set `available = false` on every demo product, correct prices and
`cost_price` on anything the store genuinely stocks, and zero the stock of the
rest.

**Either path:** fix the stock. Every seeded variant has `on_hand = 100`
(`V4_2`). Use the admin inventory screen's bulk correction so each movement is
recorded in the `stock_movements` ledger, rather than `UPDATE`-ing the table —
the ledger is the audit trail, and a direct update leaves an unexplained jump.

Verify: browse the storefront as a customer and confirm what you see matches
what the store actually sells, at the right prices.

### 2.2 Set the real store settings (A3)

Through the admin store-settings screen (`AdminStoreController`), which
invalidates the `activeStore` cache correctly — a direct `UPDATE` would leave
the cached row serving stale values for up to 60 seconds:

- **Exact storefront lat/lng.** `V8_2` seeds `12.21, 76.89` and its own comment
  flags them as approximate pending client confirmation. Get the real
  coordinates (drop a pin at the shop door, not the building centroid) — this
  point is the origin of the Haversine radius gate, so an error here refuses
  real customers or accepts out-of-range ones.
- Delivery radius, opening/closing times, minimum order value (seeded ₹499),
  support phone.

Then verify on the droplet that the testing override is **not** set — it
silently replaces the store's coordinates and logs a warning at boot:

```bash
grep -E 'TOWNBASKET_SERVICEABILITY_STORE_(LAT|LNG)' ~/Ecomm/infra/deploy/.env
# expect no output
docker compose -f docker-compose.prod.yml logs api | grep -i 'OVERRIDDEN'
# expect no output
```

Verify the gate: `GET /api/v1/serviceability/check?lat=…&lng=…` with coordinates
just inside and just outside the radius, and confirm the boundary is where you
expect on a map.

### 2.3 GSTIN and invoice series (A7)

In `.env`, before the first invoice is issued — the series is immutable once
numbers are handed out:

```
TOWNBASKET_INVOICE_GSTIN=<the store's GSTIN>
TOWNBASKET_INVOICE_SERIES_PREFIX=TB     # ≤ 4 chars (Rule 46(b) 16-char cap)
```

Restart the API (`docker compose up -d api`), then issue one invoice and check
it renders the GSTIN and a number of the form `TB/25-26/00001`.

### 2.4 Firebase phone OTP (A8)

Configuration is already guarded, so this is operational:

1. Real Firebase project, phone auth enabled, billing attached.
2. Authorised domains include the production hosts.
3. Understand the per-SMS cost and the daily quota — this is a live spend that
   scales with signups, and an exhausted quota means nobody can log in.
4. `FIREBASE_PROJECT_ID` set in `.env` (compose refuses to start otherwise).
5. **Verify the fake is actually inactive:** send a `dev:9999999999` token to
   `/auth/phone/verify` and confirm it is rejected. Then complete a real OTP
   login on a real phone.

### 2.5 Push notifications and image upload (A9)

Both are "blank means off," so they fail silently rather than loudly.

**Web Push** — generate once, treat the private key as a secret:

```bash
npx web-push generate-vapid-keys
```

Set `WEB_PUSH_PUBLIC_KEY`, `WEB_PUSH_PRIVATE_KEY` and a `WEB_PUSH_SUBJECT`
mailbox that someone actually reads. See `NOTIFICATIONS.md`. Without this a
customer who closes the tab learns nothing about their order.

**Product images** — a DO Spaces bucket **separate from the backups bucket**
(these objects are world-readable; database dumps must never share that
policy). Set the six `CATALOG_IMAGES_*` vars. Choose
`CATALOG_IMAGES_PUBLIC_BASE_URL` carefully: it decides whether an image is ours
to delete, so changing it later orphans every image written under the old one.

Verify by uploading a photo from a phone through the admin catalogue form and
confirming it renders on the storefront.

---

## Stage 3 — Durability and observability

### 3.1 Install the backup timer (A10)

The script exists; the schedule does not. Until this is installed there are no
backups. Prefer a systemd timer over cron — it logs to the journal and surfaces
failures.

`/etc/systemd/system/tb-backup.service`:

```ini
[Unit]
Description=Town Basket nightly Postgres backup to DO Spaces
After=network-online.target

[Service]
Type=oneshot
EnvironmentFile=/root/Ecomm/infra/deploy/.env
ExecStart=/root/Ecomm/infra/deploy/backup/nightly-backup.sh
StandardOutput=journal
StandardError=journal
```

`/etc/systemd/system/tb-backup.timer`:

```ini
[Unit]
Description=Run the Town Basket backup nightly

[Timer]
# 02:30 IST — the droplet's clock may be UTC; check with `timedatectl`.
OnCalendar=*-*-* 02:30:00
Persistent=true

[Install]
WantedBy=timers.target
```

```bash
systemctl daemon-reload
systemctl enable --now tb-backup.timer
systemctl start tb-backup.service      # run once now, don't wait for 02:30
journalctl -u tb-backup.service -n 50  # confirm the upload succeeded
systemctl list-timers tb-backup.timer  # confirm the next run is scheduled
```

Confirm the object landed in Spaces, and confirm the bucket itself is private —
the script's `--acl private` protects the objects it writes, but the deploy
README is explicit that it is defence in depth, not permission to relax the
bucket.

Adjust paths if the repo is not at `/root/Ecomm`, and note `EnvironmentFile`
does not perform shell expansion — if any value in `.env` uses `$VAR`, set it
literally.

### 3.2 Alert on backup outcome (A10)

A silently failing backup looks exactly like a working one. Add an
`OnFailure=` unit that posts to whatever you will actually see (email, Telegram,
a webhook), and — more important — alert on **absence**: a backup that never
ran fires no failure handler. The cheapest reliable version is a dead-man's
switch: the script pings a healthcheck URL on success, and the monitor alerts if
no ping arrives within ~26 hours.

### 3.3 Write the restore runbook (A10)

Turn 0.2 into a document at `infra/deploy/backup/RESTORE.md`: the exact commands
you ran, the elapsed time, how to find the right dump in Spaces, and how to
point the API at a restored database. Re-test it quarterly. The deploy README
already claims a tested restore runbook exists — this is what makes that true.

### 3.4 Monitoring and uptime alerting (A11)

1. Turn on log shipping: set `COMPOSE_PROFILES=monitoring` plus the
   `GRAFANA_LOKI_*` values in `.env`, then `docker compose up -d`. Confirm logs
   arrive in Grafana Cloud.
2. External uptime checks on all four subdomains from a third-party monitor —
   something outside the droplet, so it can still alert when the droplet is the
   problem.
3. Alert on the two signals that mean lost orders: API 5xx rate, and container
   restarts.
4. Add a log alert on `leak-detection-threshold` warnings. That threshold is
   already configured at 20s precisely because there is no APM here, but nothing
   reads the output.

---

## Stage 4 — Documentation and launch gates

### 4.1 Fix `README.md` (A12)

Correct: status (not "pre-development"), Maven not Gradle, remove the deleted
`packages/api-client`, and the "no delivery-rider app — by design" claim
contradicted by `apps/delivery` having its own subdomain and deploy. Keep it
consistent with `CLAUDE.md`, which currently has to warn readers off the README.

### 4.2 Policy pages and support

Terms, privacy policy, refund/cancellation policy, contact and grievance
details, live and linked from the storefront footer. Payment gateways generally
require these before approving a merchant account, so this can gate any UPI
work. Confirm the `WEB_PUSH_SUBJECT` mailbox is monitored by a named person.

### 4.3 UAT

Run `TEST_CASES.md` against QA with real staff on real devices — specifically
including a low-end Android phone on a mobile connection, which is the stated
target. Cover the full order lifecycle through delivery OTP, and the admin and
delivery apps, not just the storefront.

### 4.4 Rehearse the rollback

Execute the documented rollback in QA once, so the first attempt is not during
an incident. Note that it does not revert migrations — confirm everyone
understands schema changes are forward-only.

---

## Quick reference

| Stage | What | Deploy needed | Reversible |
|---|---|---|---|
| 0 | Preflight, backup, restore test | no | n/a |
| 1.1–1.2 | Retire seed accounts, gate fixtures | yes | migration is forward-only |
| 1.3–1.4 | Close Swagger, api-docs, metrics | yes | yes (image rollback) |
| 1.5 | Payment boot guard | yes | yes (image rollback) |
| 2.1–2.2 | Real catalogue, stock, store settings | no | from backup only |
| 2.3–2.5 | GSTIN, Firebase, push, images | restart | yes (env) |
| 3 | Backup timer, alerting, monitoring | no | yes |
| 4 | Docs, policies, UAT, rehearsal | no | n/a |

**Do not go live until:** Stage 1 verified per 1.9 · real staff accounts exist
(1.10) · catalogue and stock are real (2.1) · store coordinates confirmed (2.2)
· GSTIN set (2.3) · real OTP verified end to end (2.4) · **backup timer
installed and one restore rehearsed** (3.1, 3.3) · uptime alerting live (3.4).
