#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# 04 — Full-Text Search (BM25 / PostgreSQL tsvector)
# ═══════════════════════════════════════════════════════════════
#
# Beschreibung:
#   Klassische Volltextsuche über PostgreSQL tsvector/tsquery.
#   Basiert auf Wort-Matching und TF-Ranking.
#
# Voraussetzungen:
#   - 02-ingest.sh ausgeführt
#
# API-Format:
#   POST /api/v1/rag/retrieve
#   Body: {"query":"...","topK":5}
#
# Erwartetes Ergebnis:
#   HTTP 200 mit Ergebnis-Array
# ═══════════════════════════════════════════════════════════════

set -euo pipefail
API_KEY="dev-key-change-me"

echo "── 04 Full-Text Search (BM25) ──"
echo "Frage: \"quantum supremacy Sycamore processor\""
echo ""

RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST "http://localhost:8086/api/v1/rag/retrieve" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{"query":"quantum supremacy Sycamore processor","topK":5}')

BODY=$(echo "$RESPONSE" | sed '$d')
STATUS=$(echo "$RESPONSE" | tail -1)

echo "HTTP ${STATUS}"
echo ""

if [ "$STATUS" = "200" ]; then
  echo "$BODY" | python3 -c "
import sys, json
data = json.load(sys.stdin)
results = data if isinstance(data, list) else data.get('results', data.get('chunks', []))
print(f'  {len(results)} Ergebnisse')
for r in results[:3]:
    title = r.get('title','?')[:50]
    print(f'    [{r.get(\"id\",\"?\")}] {title}')
" 2>/dev/null
  echo "✅ ERGEBNIS: Full-Text Search liefert Ergebnisse"
else
  echo "$BODY"
  echo "❌ ERGEBNIS: Full-Text Search fehlgeschlagen"
fi
echo ""
