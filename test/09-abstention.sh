#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# 09 — Abstention Test (Out-of-Domain Query)
# ═══════════════════════════════════════════════════════════════
#
# Beschreibung:
#   Testet ob das System eine Anfrage korrekt ablehnt, wenn
#   keine relevanten Informationen in der Wissensbasis vorhanden
#   sind. Das System sollte entweder abstain (nicht antworten)
#   oder explizit angeben, dass keine ausreichenden Infos da sind.
#
# Voraussetzungen:
#   - 02-ingest.sh muss zuerst ausgeführt worden sein
#   - Die Wissensbasis enthält KEINE Informationen über
#     Schokoladenkuchen-Rezepte
#
# Erwartetes Ergebnis:
#   HTTP 200 mit:
#     - "answer": Leere Antwort ODER Hinweis auf fehlende Infos
#     - "faithfulnessScore": Niedrig oder 0
#     - Das System darf NICHT eine erfundene Antwort liefern
#
# Was passiert im Hintergrund:
#   1. Vector Search findet keine relevanten Chunks (niedriger Score)
#   2. Faithfulness-Judge erkennt fehlende Grundlage
#   3. System gibt abstain-Antwort oder leere Antwort
# ═══════════════════════════════════════════════════════════════

set -euo pipefail
BASE="http://localhost:8086"
API_KEY="dev-key-change-me"

echo "── 09 Abstention Test (Out-of-Domain) ──"
echo "POST ${BASE}/api/v1/rag/query"
echo "Frage: \"What is the recipe for chocolate cake?\""
echo "(Erwartung: Keine relevanten Infos in der Wissensbasis)"
echo ""

RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE}/api/v1/rag/query" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{"question": "What is the recipe for chocolate cake?"}')

BODY=$(echo "$RESPONSE" | sed '$d')
STATUS=$(echo "$RESPONSE" | tail -1)

echo "HTTP Status: ${STATUS}"
echo ""

if [ "$STATUS" = "200" ]; then
  ANSWER=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('answer',''))" 2>/dev/null)
  ABSTAINED_RESP=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('abstained',True))" 2>/dev/null)

  echo "Antwort: '${ANSWER:0:200}'"
  echo "Abstained: ${ABSTAINED_RESP}"
  echo ""

  ABSTAINED=false
  if [ "$ABSTAINED_RESP" = "True" ] || [ "$ABSTAINED_RESP" = "true" ]; then
    ABSTAINED=true
  elif [ -z "$ANSWER" ] || [ "$ANSWER" = "" ]; then
    ABSTAINED=true
  elif echo "$ANSWER" | grep -qi -E "no.*information|not.*available|cannot.*answer|insufficient|don.t have|abstain|no.*relevant"; then
    ABSTAINED=true
  fi

  if [ "$ABSTAINED" = true ]; then
    echo "✅ ERGEBNIS: System hat korrekt abgelehnt (keine irrelevanten Infos erlogen)"
  else
    echo "⚠️  ERGEBNIS: System hat eine Antwort geliefert (möglicherweise Halluzination)"
  fi
else
  echo "❌ ERGEBNIS: Abstention Test fehlgeschlagen (HTTP ${STATUS})"
fi
echo ""
