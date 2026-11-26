#!/usr/bin/env bash
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <backup-file.sql.gz>" >&2
  exit 1
fi

DB_NAME="${DB_NAME:-school_wallet}"
DB_USER="${DB_USER:-school_wallet}"
backup_file="$1"

if [ ! -f "$backup_file" ]; then
  echo "Backup file not found: $backup_file" >&2
  exit 1
fi

echo "Restoring $backup_file into $DB_NAME ..."
gunzip -c "$backup_file" | psql -h "${DB_HOST:-localhost}" -p "${DB_PORT:-5432}" -U "$DB_USER" -d "$DB_NAME"
echo "Restore complete. Run the application once to verify schema compatibility."
