#!/usr/bin/env bash
set -euo pipefail

DB_NAME="${DB_NAME:-school_wallet}"
DB_USER="${DB_USER:-school_wallet}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"
stamp="$(date +%Y%m%d-%H%M%S)"
file="$BACKUP_DIR/school-wallet-$stamp.sql.gz"

pg_dump -h "${DB_HOST:-localhost}" -p "${DB_PORT:-5432}" -U "$DB_USER" -d "$DB_NAME" | gzip > "$file"
echo "Backup written to $file"

find "$BACKUP_DIR" -type f -name 'school-wallet-*.sql.gz' -mtime "+$RETENTION_DAYS" -delete
