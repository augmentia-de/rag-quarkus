#!/usr/bin/env bash
set -euo pipefail

: "${RAG_ENDPOINT:=http://localhost:8085}"
: "${RAG_API_KEY:=dev-key-change-me}"
: "${TIMEOUT:=60}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/logs"
mkdir -p "$LOG_DIR"

LOG_FILE="${LOG_DIR}/${0##*/}.log"
exec > >(tee -a "$LOG_FILE") 2>&1

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✓${NC} $1"; }
warn() { echo -e "${YELLOW}⚠${NC} $1"; }
fail() { echo -e "${RED}✗${NC} $1"; }

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting: ${0##*/}"

check_deps() {
    for cmd in docker curl mvn java; do
        if ! command -v "$cmd" &>/dev/null; then
            echo "Required command '$cmd' not found"
            exit 1
        fi
    done
}

wait_for_health() {
    local url="${RAG_ENDPOINT}/q/health"
    local elapsed=0
    echo -n "Waiting for $url "
    while ! curl -sf --connect-timeout 2 --max-time 4 "$url" >/dev/null 2>&1; do
        if (( elapsed >= TIMEOUT )); then
            echo -e "\n$(fail "Health check not ready within ${TIMEOUT}s")"
            return 1
        fi
        echo -n "."
        sleep 2
        (( elapsed += 2 ))
    done
    echo " ready (${elapsed}s)"
}

api_request() {
    local method="$1" path="$2" data="$3"
    local code body
    body=$(curl -s -w '%{http_code}' -X "$method" "${RAG_ENDPOINT}${path}" \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${RAG_API_KEY}" \
        -d "$data")
    code="${body: -3}"
    body="${body:0:${#body}-3}"
    echo "HTTP ${code}" >&2
    echo "$body"
    [[ "$code" =~ ^2 ]] || return 1
}

assert_json() {
    local actual="$1" expected="$2" description="$3"
    if echo "$actual" | jq -e "$expected" >/dev/null 2>&1; then
        ok "$description"
    else
        fail "$description"
        echo "  expected jq: $expected"
        echo "  received:    $(echo "$actual" | jq -c . 2>/dev/null || echo "$actual")"
        return 1
    fi
}
