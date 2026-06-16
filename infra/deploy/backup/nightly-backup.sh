#!/usr/bin/env bash
#
# Nightly PostgreSQL backup → DO Spaces (ARCHITECTURE.md §7/§7a).
# Run via cron or a systemd timer on the droplet, e.g. nightly at 02:30:
#   30 2 * * *  /opt/town-basket/infra/deploy/backup/nightly-backup.sh >> /var/log/tb-backup.log 2>&1
#
# Durability is the priority: backups are off-server, retained, and the restore
# runbook is tested. Alert on success AND failure.
#
# Required env (from the droplet's root-restricted .env):
#   DB_URL_PG          postgres://user:pass@host:5432/townbasket   (pg_dump form)
#   SPACES_BUCKET      e.g. town-basket-backups
#   SPACES_ENDPOINT    e.g. https://blr1.digitaloceanspaces.com
#   AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY   (Spaces keys; s3-compatible)
#   RETENTION_DAYS     default 30
set -euo pipefail

: "${DB_URL_PG:?DB_URL_PG required}"
: "${SPACES_BUCKET:?SPACES_BUCKET required}"
: "${SPACES_ENDPOINT:?SPACES_ENDPOINT required}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
dumpfile="/tmp/townbasket-${timestamp}.sql.gz"

echo "[backup] dumping database -> ${dumpfile}"
pg_dump "${DB_URL_PG}" | gzip > "${dumpfile}"

echo "[backup] uploading to s3://${SPACES_BUCKET}/db/"
aws s3 cp "${dumpfile}" "s3://${SPACES_BUCKET}/db/" --endpoint-url "${SPACES_ENDPOINT}"

echo "[backup] pruning backups older than ${RETENTION_DAYS} days"
cutoff="$(date -u -d "-${RETENTION_DAYS} days" +%Y%m%d 2>/dev/null || date -u -v-"${RETENTION_DAYS}"d +%Y%m%d)"
aws s3 ls "s3://${SPACES_BUCKET}/db/" --endpoint-url "${SPACES_ENDPOINT}" | awk '{print $4}' | while read -r key; do
	[ -z "${key}" ] && continue
	keydate="$(echo "${key}" | grep -oE '[0-9]{8}' | head -1 || true)"
	if [ -n "${keydate}" ] && [ "${keydate}" -lt "${cutoff}" ]; then
		echo "[backup] deleting old backup ${key}"
		aws s3 rm "s3://${SPACES_BUCKET}/db/${key}" --endpoint-url "${SPACES_ENDPOINT}"
	fi
done

rm -f "${dumpfile}"
echo "[backup] done at $(date -u +%Y%m%dT%H%M%SZ)"
