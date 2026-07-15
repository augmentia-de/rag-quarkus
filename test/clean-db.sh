#!/usr/bin/env bash
# Clean all RAG tables (keeps schema)
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-user}"
DB_PASS="${DB_PASS:-password}"
DB_NAME="${DB_NAME:-rag}"

export PGPASSWORD="$DB_PASS"

echo "── Cleaning database: ${DB_NAME}@${DB_HOST}:${DB_PORT} ──"

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "
  TRUNCATE rag_chunks CASCADE;
  TRUNCATE graph_nodes CASCADE;
  TRUNCATE graph_edges CASCADE;
  TRUNCATE rag_ingestion_jobs CASCADE;
"

echo "✅ All tables truncated."
