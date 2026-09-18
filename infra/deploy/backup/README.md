# Nightly database backup

`nightly-backup.sh` dumps Postgres, gzips it, uploads it to a DigitalOcean
Spaces bucket, and deletes dumps older than the retention window. It runs **on
the droplet**, on a schedule you install once.

There is no failover in this deployment — one droplet, no standby, a deliberate
trade (ARCHITECTURE.md §7/§7a). The data is protected instead, and this script
is how. **Until the timer below is installed, nothing is backing anything up.**

> If `DB_URL` points at a DigitalOcean *managed* database, the provider already
> takes its own backups with point-in-time recovery, and this dump is a second,
> independent copy you control. If Postgres runs as a container on the droplet,
> **this dump is the only copy that survives losing the box.** Check which you
> have before deciding how urgent this is:
> ```bash
> cd ~/Ecomm/infra/deploy
> grep -E '^DB_URL=' .env | sed -E 's#(//[^:]+):[^@]*@#\1:***@#'
> ```
> A host like `*.db.ondigitalocean.com:25060` is managed; `postgres:5432` with a
> `postgres` container in `docker compose ps` is self-hosted.

## A. Prerequisites on the droplet

1. The two binaries the script calls. Match the client's major version to the
   server's, or `pg_dump` refuses to dump a newer database:
   ```bash
   apt-get update && apt-get install -y postgresql-client-16 awscli
   pg_dump --version && aws --version
   ```
2. A Spaces bucket for backups, **separate from the product-images bucket**.
   Image objects are world-readable; database dumps must never share that
   policy. Create it in the DO console, region close to the droplet (e.g.
   `blr1`), and leave "Restrict File Listing" ON — the bucket must be private.
3. A Spaces access key pair (DO console → **API → Spaces Keys → Generate New
   Key**). The secret is shown once.

## B. Configure (droplet `.env`)

4. Add these to `~/Ecomm/infra/deploy/.env`. `DB_URL_PG` is the **libpq** form
   of the database URL, not the JDBC one the API uses:
   ```
   DB_URL_PG=postgres://townbasket:<password>@<db-host>:25060/townbasket?sslmode=require
   SPACES_BUCKET=town-basket-backups
   SPACES_ENDPOINT=https://blr1.digitaloceanspaces.com
   AWS_ACCESS_KEY_ID=<spaces key>
   AWS_SECRET_ACCESS_KEY=<spaces secret>
   RETENTION_DAYS=30
   ```
   `chmod 600 .env`. These are credentials to every order the shop has taken.
5. Prove it works before scheduling it, so a failure is something you watch
   rather than something you discover:
   ```bash
   cd ~/Ecomm/infra/deploy
   set -a; . ./.env; set +a
   ./backup/nightly-backup.sh
   ```
   Expect four `[backup]` lines and a final `done`. Then confirm the object is
   really there, and that its size is plausible rather than a few hundred bytes
   of error text:
   ```bash
   aws s3 ls "s3://$SPACES_BUCKET/db/" --endpoint-url "$SPACES_ENDPOINT" --human-readable
   ```

## C. Schedule it (systemd, not cron)

A timer logs to the journal and its failures are visible to `systemctl`; a cron
job emails root on a box where nobody reads root's mail.

6. `/etc/systemd/system/tb-backup.service`:
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
7. `/etc/systemd/system/tb-backup.timer`:
   ```ini
   [Unit]
   Description=Run the Town Basket backup nightly

   [Timer]
   # The droplet's clock is probably UTC — check with `timedatectl`. 21:00 UTC
   # is 02:30 IST, i.e. after the shop has closed and before anyone opens it.
   OnCalendar=*-*-* 21:00:00
   Persistent=true

   [Install]
   WantedBy=timers.target
   ```
8. Enable it, then run it once immediately rather than waiting for the night:
   ```bash
   systemctl daemon-reload
   systemctl enable --now tb-backup.timer
   systemctl start tb-backup.service
   journalctl -u tb-backup.service -n 50 --no-pager
   systemctl list-timers tb-backup.timer
   ```

Two things that bite here. Adjust the paths if the repo is not at `/root/Ecomm`.
And `EnvironmentFile` is **not** a shell: it does no `$VAR` expansion and keeps
quotes literally, so write every value out in full in `.env`.

## D. Alert on it

A silently failing backup looks exactly like a working one, and a backup that
never ran fires no failure handler at all. Two different alarms:

- **Failure** — add `OnFailure=` to the service unit, pointing at a unit that
  notifies you somewhere you actually look.
- **Absence** — a dead-man's switch. Create a check at a service such as
  Healthchecks.io, have the script `curl` its ping URL on success, and let the
  monitor shout if no ping arrives within ~26 hours. This is the one that
  catches a droplet that has stopped running timers at all.

The script does not ping anything today. Adding it is a two-line change at the
end of the script plus a `HEALTHCHECK_URL` in `.env`.

## E. Prove you can get the data back

A dump nobody has restored is not a backup, it is a file. The procedure, with
the commands and the timing, is in [RESTORE.md](RESTORE.md). Walk it once now,
and again each quarter.

## What the script does, precisely

| Step | Detail |
|---|---|
| Dump | `pg_dump "$DB_URL_PG" \| gzip` to `/tmp/townbasket-<UTC timestamp>.sql.gz` |
| Upload | `aws s3 cp … --acl private` to `s3://$SPACES_BUCKET/db/` |
| Prune | Deletes objects under `db/` whose first 8-digit run is older than `RETENTION_DAYS` |
| Clean up | Removes the local dump on success |

`--acl private` is explicit rather than left to the bucket default, because
these objects sit under timestamp-predictable keys: a bucket accidentally
created with public listing would make every customer's data enumerable by
date. It is defence in depth for the objects, not permission to relax the
bucket.

Known rough edges, none of them blocking, all small:

- The local dump in `/tmp` is only removed on success, so a run that fails
  during upload leaves the file behind.
- Nothing checks the dump's size before uploading. `pipefail` catches a
  `pg_dump` that exits non-zero, but not one that writes something useless.
- No healthcheck ping, as above.
