#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# 05 — Hybrid Search (Vector + BM25 kombiniert)
# ═══════════════════════════════════════════════════════════════
#
# Beschreibung:
#   Kombiniert Vector Search und Full-Text Search.
#   Beide Suchen laufen parallel, Ergebnisse werden mit
#   Reciprocal Rank Fusion (RRF) zusammengeführt.
#
# Voraussetzungen:
#   - 02-ingest.sh muss zuerst ausgeführt worden sein
#
# Erwartetes Ergebnis:
#   HTTP 200 mit "results"-Array
#   Ergebnisse sind nach fusedem Score sortiert
#
# Was passiert im Hintergrund:
#   1. Parallele Ausführung: Vector Search + BM25
#   2. RRF-Fusion: score = Σ 1/(k + rank_i) pro Dokument
#   3. Top-K Ergebnisse nach fusedem Score
# ═══════════════════════════════════════════════════════════════

set -euo pipefail
BASE="http://localhost:8086"
API_KEY="dev-key-change-me"

echo "── 05 Hybrid Search (Vector + BM25) ──"
echo "POST ${BASE}/api/v1/rag/retrieve"
echo "Suche: \"Apollo 11 Moon landing astronauts\""
echo ""

RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE}/api/v1/rag/retrieve" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{"query": "Apollo 11 Moon landing astronauts", "topK": 5}')

BODY=$(echo "$RESPONSE" | sed '$d')
STATUS=$(echo "$RESPONSE" | tail -1)

echo "HTTP Status: ${STATUS}"
echo ""

if [ "$STATUS" = "200" ]; then
  echo "Ergebnisse:"
  echo "$BODY" | python3 -c "
import sys, json
data = json.load(sys.stdin)
results = data if isinstance(data, list) else data.get('results', [])
print(f'  Anzahl: {len(results)}')
for i, r in enumerate(results):
    title = r.get('title','?')[:50]
    cid = r.get('id', '?')
    print(f'  [{i+1}] {title}  id={cid}')
" 2>/dev/null
  echo ""

  COUNT=$(echo "$BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d) if isinstance(d,list) else len(d.get('results',[])))" 2>/dev/null)
  if [ "$COUNT" -gt 0 ]; then
    echo "✅ ERGEBNIS: Hybrid Search liefert ${COUNT} Ergebnisse"
  else
    echo "❌ ERGEBNIS: Hybrid Search liefert 0 Ergebnisse"
  fi
else
  echo "❌ ERGEBNIS: Hybrid Search fehlgeschlagen (HTTP ${STATUS})"
fi
echo ""
