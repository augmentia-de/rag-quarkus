#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# 01 — Health Check
# ═══════════════════════════════════════════════════════════════
#
# Beschreibung:
#   Prüft ob die Anwendung erreichbar ist und die DB-Verbindung steht.
#
# Voraussetzungen:
#   - Quarkus läuft auf http://localhost:8086
#   - Kein API-Key nötig (Health-Endpoint ist öffentlich)
#
# Erwartetes Ergebnis:
#   HTTP 200 mit JSON: "status":"UP"
#   Enthält Checks für: llm, Database, postgresql
# ═══════════════════════════════════════════════════════════════

set -euo pipefail

echo "── 01 Health Check ──"

RESPONSE=$(curl -s -w '\n%{http_code}' http://localhost:8086/q/health)
BODY=$(echo "$RESPONSE" | sed '$d')
STATUS=$(echo "$RESPONSE" | tail -1)

echo "HTTP ${STATUS}"
echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
echo ""

if [ "$STATUS" = "200" ]; then
  echo "✅ ERGEBNIS: Health Check bestanden"
else
  echo "❌ ERGEBNIS: Health Check fehlgeschlagen"
fi
echo ""
