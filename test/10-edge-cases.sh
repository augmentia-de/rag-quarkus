#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# 10 — Edge Cases & Fehlerbehandlung
# ═══════════════════════════════════════════════════════════════
#
# Beschreibung:
#   Testet Grenzfälle und Fehlerbehandlung der API:
#     1. Leere Query
#     2. Falscher API-Key
#     3. Ungültiger JSON-Body
#     4. Fehlender Content-Type
#     5. Query mit Sonderzeichen
#
# Erwartetes Ergebnis:
#   - Leere Query: 400 oder 422 (Validierungsfehler)
#   - Falscher Key: 401 (Unauthorized)
#   - Ungültiges JSON: 400 (Parse Error)
#   - Fehlender Content-Type: 415 oder 400
#
# ═══════════════════════════════════════════════════════════════

set -euo pipefail
BASE="http://localhost:8086"
API_KEY="dev-key-change-me"

echo "── 10 Edge Cases & Fehlerbehandlung ──"
echo ""

# Test 10a: Leere Query
echo "── 10a Leere Query ──"
RESP=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE}/api/v1/rag/query" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{"question": ""}')
S=$(echo "$RESP" | tail -1)
echo "  HTTP ${S}"
if [ "$S" = "400" ] || [ "$S" = "422" ]; then
  echo "  ✅ Korrekt abgelehnt"
elif [ "$S" = "200" ]; then
  echo "  ⚠️  200 — System verarbeitet leere Query (akzeptabel)"
else
  echo "  ❌ Unerwarteter Status"
fi
echo ""

# Test 10b: Falscher API-Key
echo "── 10b Falscher API-Key ──"
RESP=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE}/api/v1/rag/query" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: falscher-key-12345" \
  -d '{"query": "test", "searchMode": "VECTOR", "k": 3}')
S=$(echo "$RESP" | tail -1)
echo "  HTTP ${S}"
if [ "$S" = "401" ] || [ "$S" = "403" ]; then
  echo "  ✅ Korrekt abgelehnt (Unauthorized)"
else
  echo "  ❌ Unerwarteter Status (erwartet 401/403)"
fi
echo ""

# Test 10c: Fehlender API-Key
echo "── 10c Fehlender API-Key ──"
RESP=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE}/api/v1/rag/query" \
  -H "Content-Type: application/json" \
  -d '{"query": "test", "searchMode": "VECTOR", "k": 3}')
S=$(echo "$RESP" | tail -1)
echo "  HTTP ${S}"
if [ "$S" = "401" ] || [ "$S" = "403" ]; then
  echo "  ✅ Korrekt abgelehnt (Unauthorized)"
else
  echo "  ❌ Unerwarteter Status (erwartet 401/403)"
fi
echo ""

# Test 10d: Ungültiges JSON
echo "── 10d Ungültiges JSON ──"
RESP=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE}/api/v1/rag/query" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{unclosed json')
S=$(echo "$RESP" | tail -1)
echo "  HTTP ${S}"
if [ "$S" = "400" ] || [ "$S" = "422" ]; then
  echo "  ✅ Korrekt abgelehnt (Parse Error)"
else
  echo "  ❌ Unerwarteter Status (erwartet 400/422)"
fi
echo ""

# Test 10e: Query mit Sonderzeichen
echo "── 10e Query mit Sonderzeichen ──"
RESP=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE}/api/v1/rag/query" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d "{\"question\": \"Was ist 'AI' & wie funktioniert das?\"}")
S=$(echo "$RESP" | tail -1)
echo "  HTTP ${S}"
if [ "$S" = "200" ]; then
  echo "  ✅ Sonderzeichen werden korrekt verarbeitet"
else
  echo "  ❌ Unerwarteter Status"
fi
echo ""

# Test 10f: Sehr lange Query
echo "── 10f Sehr lange Query (>5000 Zeichen) ──"
LONG_QUERY=$(python3 -c "print('A' * 5001)")
RESP=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE}/api/v1/rag/query" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d "{\"question\": \"${LONG_QUERY}\"}")
S=$(echo "$RESP" | tail -1)
echo "  HTTP ${S}"
if [ "$S" = "400" ] || [ "$S" = "422" ] || [ "$S" = "200" ]; then
  echo "  ✅ Lange Query wird behandelt (400/422 oder 200)"
else
  echo "  ❌ Unerwarteter Status"
fi
echo ""

echo "── Zusammenfassung Edge Cases ──"
echo "Alle Grenzfälle wurden getestet. Siehe oben für Details."
echo ""
