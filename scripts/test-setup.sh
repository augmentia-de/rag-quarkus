#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

source "$SCRIPT_DIR/test-common.sh"

NO_CHECK=false
DEBUG_PORT=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --no-check) NO_CHECK=true; shift ;;
        --debug) DEBUG_PORT="${2:-5005}"; if [[ $# -gt 1 ]]; then shift 2; else shift; fi ;;
        --debug=*) DEBUG_PORT="${1#*=}"; shift ;;
        *) shift ;;
    esac
done

echo "=== RAG Test: Setup ==="
$NO_CHECK || check_deps

# ------------------------------------------------------------------
# 1. Verify existing PostgreSQL container
# ------------------------------------------------------------------
PG_CONTAINER="postgres-vector"
if ! $NO_CHECK; then
    if ! docker ps --format '{{.Names}}' | grep -q "^${PG_CONTAINER}$"; then
        fail "Container '${PG_CONTAINER}' not found. Start it first."
        echo "  docker start ${PG_CONTAINER}"
        exit 1
    fi

    PG_STATUS=$(docker inspect --format='{{.State.Health.Status}}' "$PG_CONTAINER" 2>/dev/null || echo "running")
    if [[ "$PG_STATUS" != "healthy" && "$PG_STATUS" != "running" ]]; then
        fail "Container '${PG_CONTAINER}' is ${PG_STATUS} (needs healthy)"
        exit 1
    fi
    ok "PostgreSQL container '${PG_CONTAINER}' is ${PG_STATUS}"
fi

# ------------------------------------------------------------------
# 2. Connection details
# ------------------------------------------------------------------
PG_HOST="localhost"
PG_PORT="5432"
PG_USER="user"
PG_PASSWORD="password"
PG_DB="rag"
OLLAMA_URL="http://localhost:11434"
CHAT_MODEL="${CHAT_MODEL:-qwen3.5:2b}"
EMBEDDING_MODEL="${EMBEDDING_MODEL:-mxbai-embed-large:latest}"
RAG_PORT="${RAG_PORT:-8085}"

# ------------------------------------------------------------------
# 3. Create database + run init.sql
# ------------------------------------------------------------------
if ! $NO_CHECK; then
    echo "Setting up '${PG_DB}' database..."
    docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d postgres \
        -tc "SELECT 1 FROM pg_database WHERE datname='${PG_DB}'" \
        | grep -q 1 || {
        docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d postgres \
            -c "CREATE DATABASE ${PG_DB}"
        ok "Database '${PG_DB}' created"
    }

    PGVECTOR_OK=$(docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" \
        -tc "SELECT 1 FROM pg_extension WHERE extname='vector'" | tr -d ' ')
    if [[ "$PGVECTOR_OK" != "1" ]]; then
        docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" \
            < "$PROJECT_DIR/src/main/resources/db/init.sql"
        ok "pgvector extension and rag_chunks table created"
    fi

    # ------------------------------------------------------------------
    # 4. Detect Ollama
    # ------------------------------------------------------------------
    if curl -sf "${OLLAMA_URL}/api/tags" >/dev/null 2>&1; then
        ok "Ollama running at ${OLLAMA_URL}"
        if curl -sf "${OLLAMA_URL}/api/tags" | jq -e ".models[] | select(.name == \"${CHAT_MODEL}\")" >/dev/null 2>&1; then
            ok "Chat model '${CHAT_MODEL}' available"
        else
            warn "Chat model '${CHAT_MODEL}' not found — pulling..."
            ollama pull "${CHAT_MODEL}"
        fi
        if curl -sf "${OLLAMA_URL}/api/tags" | jq -e ".models[] | select(.name == \"${EMBEDDING_MODEL}\")" >/dev/null 2>&1; then
            ok "Embedding model '${EMBEDDING_MODEL}' available"
        else
            warn "Embedding model '${EMBEDDING_MODEL}' not found — pulling..."
            ollama pull "${EMBEDDING_MODEL}"
        fi
    else
        warn "Ollama not reachable at ${OLLAMA_URL} — LLM-dependent features will fail"
    fi
fi

# ------------------------------------------------------------------
# 5. Build the application
# ------------------------------------------------------------------
if ! $NO_CHECK; then
    echo "Building project..."
    mvn package -DskipTests -q
    ok "Build complete"
fi

# ------------------------------------------------------------------
# 6. Start Quarkus (background, production mode with java -jar)
# ------------------------------------------------------------------
echo "Starting Quarkus on port ${RAG_PORT}..."
PID_FILE="$PROJECT_DIR/target/.quarkus-dev.pid"
if [[ -f "$PID_FILE" ]]; then
    old_pid=$(cat "$PID_FILE")
    if kill -0 "$old_pid" 2>/dev/null; then
        warn "Quarkus already running (PID $old_pid) — stopping first"
        kill "$old_pid" 2>/dev/null || true
        sleep 2
    fi
fi

RUNNER_JAR="$PROJECT_DIR/target/quarkus-app/quarkus-run.jar"
if [[ ! -f "$RUNNER_JAR" ]]; then
    fail "Runner JAR not found at $RUNNER_JAR — package first"
    exit 1
fi

LOGFILE="$LOG_DIR/quarkus-dev.log"
QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://${PG_HOST}:${PG_PORT}/${PG_DB}" \
QUARKUS_DATASOURCE_USERNAME="${PG_USER}" \
QUARKUS_DATASOURCE_PASSWORD="${PG_PASSWORD}" \
QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL="${OLLAMA_URL}/v1" \
QUARKUS_LANGCHAIN4J_OPENAI_API_KEY="ollama" \
QUARKUS_LANGCHAIN4J_OPENAI_CHAT_MODEL_MODEL_NAME="${CHAT_MODEL}" \
QUARKUS_LANGCHAIN4J_OPENAI_EMBEDDING_MODEL_MODEL_NAME="${EMBEDDING_MODEL}" \
RAG_LLM_ENDPOINT="${OLLAMA_URL}/v1" \
RAG_LLM_MODEL="${CHAT_MODEL}" \
RAG_EMBEDDING_ENDPOINT="${OLLAMA_URL}/v1" \
RAG_EMBEDDING_MODEL="${EMBEDDING_MODEL}" \
RAG_AUTH_API_KEY="${RAG_API_KEY}" \
    java -Dquarkus.http.port="${RAG_PORT}" \
    $(if [[ "$DEBUG_PORT" -gt 0 ]]; then echo "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT}"; fi) \
    -jar "$RUNNER_JAR" \
    > "$LOGFILE" 2>&1 &
QUARKUS_PID=$!
echo "$QUARKUS_PID" > "$PID_FILE"
ok "Quarkus started (PID $QUARKUS_PID, log: $LOGFILE)"

# ------------------------------------------------------------------
# 7. Wait for Quarkus to be ready
# ------------------------------------------------------------------
echo "Checking: RAG_PORT=${RAG_PORT} RAG_ENDPOINT=${RAG_ENDPOINT} quarkus.http.port=8085"
if wait_for_health; then
    ok "RAG engine is UP at ${RAG_ENDPOINT}"
else
    fail "RAG engine failed to start — check $LOGFILE"
    tail -30 "$LOGFILE"
    exit 1
fi

echo "---"
echo "Setup complete. Run:"
echo "  bash scripts/test-ingest.sh     # Ingest sample documents"
echo "  bash scripts/test-retrieval.sh  # Test query endpoint"
echo "  bash scripts/test-agent.sh      # Full agent tests"
echo ""
echo "Logs: ${LOG_DIR}/"
echo "Stop:  kill \$(cat $PID_FILE) 2>/dev/null"
