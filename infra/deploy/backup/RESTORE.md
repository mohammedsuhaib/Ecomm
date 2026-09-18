# Restoring the database

The procedure for getting Town Basket's data back out of a nightly dump. Walk
it once when you set backups up, and once a quarter after that — this document
is only true for as long as someone has actually run it.

**Before you start, write down the time.** How long this takes *is* your
recovery-time expectation, and it is the only honest answer to "how long would
we be down?".

## 1. Decide what you are restoring into

Never restore over a live database as your first move. Restore beside it, look
at what you got, then switch.

| Situation | Restore into |
|---|---|
| Testing the backup (the quarterly drill) | A scratch database, e.g. `tb_restore_test` |
| Corruption or a bad migration, data still reachable | A scratch database, then swap the API over |
| The droplet or the database is gone | A freshly created database on new infrastructure |

If the database is DigitalOcean managed, check its own point-in-time recovery
first — restoring to a moment before the damage usually beats last night's
dump, because it loses less. These dumps are the copy you control and the one
that survives losing the whole DigitalOcean account; both are worth having.

## 2. Find the right dump

Keys are timestamped in UTC: `townbasket-20260918T210000Z.sql.gz`.

```bash
cd ~/Ecomm/infra/deploy
set -a; . ./.env; set +a

aws s3 ls "s3://$SPACES_BUCKET/db/" --endpoint-url "$SPACES_ENDPOINT" --human-readable
```

Pick the newest one from **before** the damage, not simply the newest. Then:

```bash
aws s3 cp "s3://$SPACES_BUCKET/db/townbasket-<timestamp>.sql.gz" /tmp/ \
  --endpoint-url "$SPACES_ENDPOINT"

ls -lh /tmp/townbasket-<timestamp>.sql.gz    # a few hundred bytes means a failed dump
gunzip -t /tmp/townbasket-<timestamp>.sql.gz && echo "archive is intact"
```

## 3. Restore into a scratch database

`DB_URL_PG` carries the host, port, user and password. Read them once into
shell variables so the commands below stay short:

```bash
PGHOST=$(echo "$DB_URL_PG" | sed -E 's#.*@([^:/]+).*#\1#')
PGPORT=$(echo "$DB_URL_PG" | sed -E 's#.*:([0-9]+)/.*#\1#')
PGUSER=$(echo "$DB_URL_PG" | sed -E 's#.*://([^:]+):.*#\1#')
export PGPASSWORD=$(echo "$DB_URL_PG" | sed -E 's#.*://[^:]+:([^@]*)@.*#\1#')
export PGHOST PGPORT PGUSER

createdb tb_restore_test
gunzip -c /tmp/townbasket-<timestamp>.sql.gz | psql -d tb_restore_test
```

A managed database may refuse `createdb` for a restricted user; create it from
the DigitalOcean console instead and carry on.

## 4. Check you restored something real

Row counts, not a successful exit code. An empty restore also exits zero.

```bash
psql -d tb_restore_test -c "SELECT count(*) FROM orders.orders;"
psql -d tb_restore_test -c "SELECT count(*) FROM catalog.products;"
psql -d tb_restore_test -c "SELECT count(*) FROM identity.users;"
psql -d tb_restore_test -c "SELECT max(placed_at) FROM orders.orders;"
```

That last one tells you how much you lost: everything after it is gone, which
is up to 24 hours of orders with a nightly schedule. Also confirm Flyway's
history came across, because the API refuses to start against a schema it
cannot validate:

```bash
psql -d tb_restore_test -c "SELECT version, description, success FROM flyway.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

## 5. Point the API at the restored database

Only for a real recovery. For the quarterly drill, skip to step 6.

```bash
cd ~/Ecomm/infra/deploy
cp .env .env.bak-$(date -u +%Y%m%dT%H%M%SZ)   # keep the old values
nano .env                                      # DB_URL  -> the restored database
                                               # DB_URL_PG -> the same, libpq form
docker compose -f docker-compose.prod.yml up -d api
docker compose -f docker-compose.prod.yml logs -f api
```

Watch for Flyway validating cleanly and the app reporting it is listening. Then
check it from outside, and look at the shop itself:

```bash
curl -fsS https://api.town-basket.com/actuator/health
```

Sign in to the admin app and confirm the order queue holds the orders you
expect. Both `DB_URL` and `DB_URL_PG` must point at the same place afterwards,
or tonight's backup silently dumps the old database.

## 6. Clean up and record what happened

```bash
dropdb tb_restore_test
rm -f /tmp/townbasket-*.sql.gz
unset PGPASSWORD
```

Write down, in the incident notes or below: which dump you used, how long the
restore took end to end, and anything that surprised you. Fix the surprise
before the next drill.

| Date | Dump used | Elapsed | Ran by | Notes |
|---|---|---|---|---|
| | | | | |
