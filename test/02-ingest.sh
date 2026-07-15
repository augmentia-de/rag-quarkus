#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# 02 — Dokumente ingested (3 Dokumente, volle Pipeline)
# ═══════════════════════════════════════════════════════════════
#
# Beschreibung:
#   Ingested 3 Testdokumente (AI History, Quantum Computing,
#   Space Exploration) durch die volle Pipeline:
#     Chunking → Contextualization (LLM/OpenRouter)
#              → Graph Extraction (LLM/OpenRouter)
#              → Embeddings (Ollama)
#
# Voraussetzungen:
#   - Quarkus im dev-mode (echtes LLM, nicht Mocks!)
#   - Ollama mit mxbai-embed-large:latest
#   - Netzwerkzugang zu openrouter.ai
#
# API-Format:
#   POST /api/v1/rag/ingest
#   Header: X-API-Key: dev-key-change-me
#   Body: {"documents": [{"id":"...","docId":"...","title":"...","text":"..."}]}
#
# Erwartetes Ergebnis:
#   HTTP 202 mit {"jobId":"<uuid>","message":"..."}
#
# Dauer: ca. 30-120s (LLM-Aufrufe für Contextualization + Graph)
# ═══════════════════════════════════════════════════════════════

set -euo pipefail
API_KEY="dev-key-change-me"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAYLOAD="${SCRIPT_DIR}/../test-data/ingest-payload.json"

echo "── 02 Dokumente ingested (3 Dokumente) ──"
echo "POST http://localhost:8086/api/v1/rag/ingest"
echo ""

RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST "http://localhost:8086/api/v1/rag/ingest" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d @"${PAYLOAD}")

BODY=$(echo "$RESPONSE" | sed '$d')
STATUS=$(echo "$RESPONSE" | tail -1)

echo "HTTP ${STATUS}"
echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
echo ""

if [ "$STATUS" = "202" ]; then
  JOB_ID=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('jobId',''))" 2>/dev/null)
  echo "✅ Ingest akzeptiert — Job-ID: ${JOB_ID}"
  echo ""
  echo "Warte auf Abschluss (LLM-Aufrufe laufen)..."
  for i in $(seq 1 80); do
    sleep 5
    STATUS_RESP=$(curl -sf -H "X-API-Key: ${API_KEY}" "http://localhost:8086/api/v1/rag/ingest/${JOB_ID}" 2>/dev/null || echo "{}")
    JOB_STATUS=$(echo "$STATUS_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "?")
    echo "  [${i}] ${JOB_STATUS} ($((i*5))s)"
    if [ "$JOB_STATUS" = "DONE" ]; then
      echo ""
      echo "✅ ABGESCHLOSSEN: 3 Dokumente ingested"
      echo "   → Chunks mit Embeddings in PostgreSQL"
      echo "   → Graph (Entitäten + Beziehungen) extrahiert"
      break
    fi
    if [ "$JOB_STATUS" = "FAILED" ]; then
      echo ""
      echo "❌ FEHLER: Ingestion fehlgeschlagen"
      echo "$STATUS_RESP" | python3 -m json.tool 2>/dev/null || echo "$STATUS_RESP"
      break
    fi
  done
else
  echo "❌ ERGEBNIS: Ingest fehlgeschlagen (HTTP ${STATUS})"
fi
echo ""
