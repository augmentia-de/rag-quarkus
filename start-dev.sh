#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"


# ---- Quarkus Dev Mode ----
exec mvn quarkus:dev \
    -Dquarkus.http.port="8086" \
    -Dloop.config.dir="$SCRIPT_DIR/config" \
    -DskipTests=true
