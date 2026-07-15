#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# 10 — Vergleich: Verschiedene Fragen an die gleiche Wissensbasis
# ═══════════════════════════════════════════════════════════════
#
# Beschreibung:
#   Führt mehrere Fragen aus verschiedenen Domänen gegen die
#   gleiche ingested Wissensbasis aus, um die Qualität der
#   RAG-Pipeline über verschiedene Themen zu testen.
#
# Voraussetzungen:
#   - 02-ingest.sh ausgeführt
#
# ═══════════════════════════════════════════════════════════════

set -euo pipefail
API_KEY="dev-key-change-me"

echo "── 10 Multi-Domain Vergleichstest ──"
echo ""

QUESTIONS=(
  "Who developed AlphaGo and when did it beat a world champion?"
  "What is Shor's algorithm and why is it significant?"
  "How many people walked on the Moon during the Apollo program?"
  "Who are the founders of OpenAI and Anthropic?"
  "What is the James Webb Space Telescope and when was it launched?"
)

for i in "${!QUESTIONS[@]}"; do
  Q="${QUESTIONS[$i]}"
  echo "── Frage $((i+1)): ${Q} ──"
  RESPONSE=$(curl -s -w '\n%{http_code}' \
    -X POST "http://localhost:8086/api/v1/rag/query" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${API_KEY}" \
    -d "{\"question\":\"${Q}\"}")
  BODY=$(echo "$RESPONSE" | sed '$d')
  STATUS=$(echo "$RESPONSE" | tail -1)

  if [ "$STATUS" = "200" ]; then
    ANSWER=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('answer',''))" 2>/dev/null)
    FAITH=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('faithfulnessScore',0))" 2>/dev/null)
    CITS=$(echo "$BODY" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('citations',[])))" 2>/dev/null)
    echo "  Faithfulness: ${FAITH} | Citations: ${CITS}"
    echo "  Answer: ${ANSWER:0:150}..."
  else
    echo "  HTTP ${STATUS}"
  fi
  echo ""
done
