#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# 06 — Full RAG Query (mit LLM-Generierung + Faithfulness)
# ═══════════════════════════════════════════════════════════════
#
# Beschreibung:
#   Vollständiger RAG-Pipeline-Test mit echtem LLM:
#     1. Query-Embedding via Ollama
#     2. Vector Search via pgvector
#     3. CrossEncoder-Reranking
#     4. LLM-Antwortgenerierung via OpenRouter (gpt-oss-120b)
#     5. Faithfulness-Judge via OpenRouter (Glaubwürdigkeitsprüfung)
#
# Voraussetzungen:
#   - 02-ingest.sh muss zuerst ausgeführt worden sein
#   - Ollama läuft mit mxbai-embed-large:latest
#   - Netzwerkzugang zu openrouter.ai
#
# Erwartetes Ergebnis:
#   HTTP 200 mit JSON-Body der:
#     - "answer": Die LLM-generierte Antwort (string)
#     - "citations": Array mit chunkId + excerpt pro Quelle
#     - "faithfulnessScore": Score 0.0-1.0 (wie gut belegt)
#
# Was passiert im Hintergrund:
#   → 2 LLM-Aufrufe an OpenRouter (Generator + Judge)
#   → 1 Embedding-Aufruf an Ollama
#   → pgvector-Suche + CrossEncoder-Reranking
# ═══════════════════════════════════════════════════════════════

set -euo pipefail
BASE="http://localhost:8086"
API_KEY="dev-key-change-me"

echo "── 06 Full RAG Query (LLM + Faithfulness) ──"
echo "POST ${BASE}/api/v1/rag/query"
echo "Frage: \"Who created the transformer architecture and what paper introduced it?\""
echo ""

RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE}/api/v1/rag/query" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{"question": "Who created the transformer architecture and what paper introduced it?"}')

BODY=$(echo "$RESPONSE" | sed '$d')
STATUS=$(echo "$RESPONSE" | tail -1)

echo "HTTP Status: ${STATUS}"
echo ""

if [ "$STATUS" = "200" ]; then
  echo "Antwort:"
  echo "$BODY" | python3 -c "
import sys, json
data = json.load(sys.stdin)
answer = data.get('answer', '')
citations = data.get('citations', [])
verification = data.get('verification', [])
print(f'  Answer: {answer[:300]}')
print(f'  Verification: {len(verification)} result(s)')
print(f'  Citations ({len(citations)}):')
for c in citations[:3]:
    print(f'    {c}')
" 2>/dev/null
  echo ""

  ABSTAINED=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('abstained',True))" 2>/dev/null)
  ANSWER=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('answer',''))" 2>/dev/null)
  ABSTAINED=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('abstained',False))" 2>/dev/null)

  CHECKS=0
  PASSED=0

  CHECKS=$((CHECKS+1))
  if [ -n "$ANSWER" ] && [ "$ANSWER" != "" ]; then
    echo "  ✅ Antwort nicht leer"
    PASSED=$((PASSED+1))
  else
    echo "  ❌ Antwort ist leer"
  fi

  CHECKS=$((CHECKS+1))
  if [ "$ABSTAINED" = "False" ] || [ "$ABSTAINED" = "false" ]; then
    echo "  ✅ Nicht abgestained"
    PASSED=$((PASSED+1))
  else
    echo "  ❌ System hat abgestained"
  fi

  CHECKS=$((CHECKS+1))
  if echo "$ANSWER" | grep -qi -E "vaswani|transformer|attention|google"; then
    echo "  ✅ Antwort enthält relevante Keywords"
    PASSED=$((PASSED+1))
  else
    echo "  ❌ Antwort enthält keine relevanten Keywords"
  fi

  echo ""
  if [ $PASSED -eq $CHECKS ]; then
    echo "✅ ERGEBNIS: Full RAG Query funktioniert (${PASSED}/${CHECKS} Checks bestanden)"
  else
    echo "⚠️  ERGEBNIS: ${PASSED}/${CHECKS} Checks bestanden"
  fi
else
  echo "❌ ERGEBNIS: Full RAG Query fehlgeschlagen (HTTP ${STATUS})"
fi
echo ""
