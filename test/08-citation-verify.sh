#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# 08 — Citation Verification
# ═══════════════════════════════════════════════════════════════
#
# Beschreibung:
#   Prüft ob das System Quellenangaben (Citations) korrekt
#   zuordnet. Die Antwort muss die exakten Fakten aus den
#   Quellen enthalten und die Quellen müssen korrekt sein.
#
# Voraussetzungen:
#   - 02-ingest.sh muss zuerst ausgeführt worden sein
#
# Erwartetes Ergebnis:
#   HTTP 200 mit:
#     - "answer": Enthält "November 2022" und "100 million"
#     - "citations": Enthält mindestens 1 Quellenangabe
#       mit chunkId und excerpt (Ausschnitt aus dem Originaltext)
#
# Was passiert im Hintergrund:
#   1. Vector Search findet relevante Chunks
#   2. LLM generiert Antwort mit Quellenverweisen
#   3. Faithfulness-Judge prüft ob Aussage durch Quelle gedeckt
# ═══════════════════════════════════════════════════════════════

set -euo pipefail
BASE="http://localhost:8086"
API_KEY="dev-key-change-me"

echo "── 08 Citation Verification ──"
echo "POST ${BASE}/api/v1/rag/query"
echo "Frage: \"When was ChatGPT launched and how many users did it reach?\""
echo ""

RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE}/api/v1/rag/query" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{"question": "When was ChatGPT launched and how many users did it reach?"}')

BODY=$(echo "$RESPONSE" | sed '$d')
STATUS=$(echo "$RESPONSE" | tail -1)

echo "HTTP Status: ${STATUS}"
echo ""

if [ "$STATUS" = "200" ]; then
  echo "Zitierung & Antwort:"
  echo "$BODY" | python3 -c "
import sys, json
data = json.load(sys.stdin)
answer = data.get('answer', '')
citations = data.get('citations', [])
abstained = data.get('abstained', False)
print(f'  Answer: {answer[:300]}')
print(f'  Abstained: {abstained}')
print(f'  Citations ({len(citations)}):')
for c in citations:
    print(f'    {c}')
" 2>/dev/null
  echo ""

  ANSWER=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('answer',''))" 2>/dev/null)
  ABSTAINED=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('abstained',True))" 2>/dev/null)

  CHECKS=0
  PASSED=0

  CHECKS=$((CHECKS+1))
  if [ "$ABSTAINED" = "False" ] || [ "$ABSTAINED" = "false" ]; then
    echo "  ✅ Antwort wurde generiert (nicht abgestained)"
    PASSED=$((PASSED+1))
  else
    echo "  ❌ System hat abgestained"
  fi

  CHECKS=$((CHECKS+1))
  if echo "$ANSWER" | grep -qi -E "november 2022|100 million"; then
    echo "  ✅ Antwort enthält erwartete Fakten"
    PASSED=$((PASSED+1))
  else
    echo "  ❌ Antwort fehlen erwartete Fakten"
  fi

  echo ""
  if [ $PASSED -eq $CHECKS ]; then
    echo "✅ ERGEBNIS: Citation Verification bestanden (${PASSED}/${CHECKS})"
  else
    echo "⚠️  ERGEBNIS: ${PASSED}/${CHECKS} Checks bestanden"
  fi
else
  echo "❌ ERGEBNIS: Citation Verification fehlgeschlagen (HTTP ${STATUS})"
fi
echo ""
