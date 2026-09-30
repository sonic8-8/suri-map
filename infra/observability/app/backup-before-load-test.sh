#!/usr/bin/env sh

set -eu
umask 077

container="${POSTGRES_CONTAINER:-suri-map-postgis}"
backup_dir="${BACKUP_DIR:-/srv/apps/suri-map/backups}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="${backup_dir}/suri-map-before-load-${timestamp}.dump"

install -d -m 700 "${backup_dir}"

docker exec "${container}" sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
  >"${backup_file}"

test -s "${backup_file}"
docker exec -i "${container}" sh -c 'pg_restore --list >/dev/null' <"${backup_file}"

printf '%s\n' "${backup_file}"
