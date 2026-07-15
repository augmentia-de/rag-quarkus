#!/usr/bin/env bash
set -euo pipefail

# ---- API-Keys laden ----
if [ -f set_keys.sh ]; then
    echo "Lade API-Keys aus set_keys.sh ..."
    source set_keys.sh
fi


SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"


# ---- Quarkus Dev Mode ----
exec mvn quarkus:dev \
    -Dquarkus.http.port="8086" \
    -Dloop.config.dir="$SCRIPT_DIR/config" \
    -DskipTests=true
