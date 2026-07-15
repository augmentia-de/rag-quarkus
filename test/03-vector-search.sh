#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# 03 — Vector Search (pgvector Cosine Similarity)
# ═══════════════════════════════════════════════════════════════
#
# Beschreibung:
#   Sucht nach ähnlichen Chunks mittels Vektorähnlichkeit.
#   Query → Ollama Embedding → pgvector Cosine Search → Top-K
#
# Voraussetzungen:
#   - 02-ingest.sh ausgeführt
#   - Ollama läuft
#
# API-Format:
#   POST /api/v1/rag/retrieve
#   Body: {"query":"...","topK":5}
#
# Erwartetes Ergebnis:
#   HTTP 200 mit Array von ChunkResultaten
#   Jedes Ergebnis: {chunkId, title, text, score, ...}
# ═══════════════════════════════════════════════════════════════

set -euo pipefail
API_KEY="dev-key-change-me"

echo "── 03 Vector Search ──"
echo "Frage: \"Who invented the transformer architecture?\""
echo ""

RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST "http://localhost:8086/api/v1/rag/retrieve" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{"query":"Who invented the transformer architecture?","topK":5}')

BODY=$(echo "$RESPONSE" | sed '$d')
STATUS=$(echo "$RESPONSE" | tail -1)

echo "HTTP ${STATUS}"
echo ""

if [ "$STATUS" = "200" ]; then
  echo "$BODY" | python3 -c "
import sys, json
data = json.load(sys.stdin)
results = data if isinstance(data, list) else data.get('results', data.get('chunks', []))
print(f'  {len(results)} Ergebnisse:')
for i, r in enumerate(results):
    title = r.get('title', '?')[:50]
    cid = r.get('id', '?')
    print(f'  [{i+1}] title={title}  id={cid}')
" 2>/dev/null
  echo ""
  echo "✅ ERGEBNIS: Vector Search liefert Ergebnisse"
else
  echo "$BODY"
  echo "❌ ERGEBNIS: Vector Search fehlgeschlagen"
fi
echo ""
