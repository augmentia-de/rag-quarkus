#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# Manuelle Test Suite — RAG Quarkus with Real LLM
# ═══════════════════════════════════════════════════════════════
#
# Voraussetzungen:
#   1. Quarkus läuft im dev-mode (NICHT test-mode mit Mocks!)
#      → source set_keys.sh && mvn quarkus:dev
#   2. Ollama läuft mit mxbai-embed-large:latest
#   3. Netzwerkzugang zu openrouter.ai
#
# Starten mit:
#   bash run-all.sh
#
# ═══════════════════════════════════════════════════════════════
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "══════════════════════════════════════════════════════════════"
echo "  Manuelle Test Suite — RAG Quarkus"
echo "══════════════════════════════════════════════════════════════"
echo ""

echo "Prüfe ob Quarkus läuft..."
if curl -sf --max-time 3 http://localhost:8086/q/health > /dev/null 2>&1; then
  echo "✅ Quarkus läuft auf http://localhost:8086"
else
  echo "❌ Quarkus läuft NICHT!"
  echo "   Starten mit: cd .. && source set_keys.sh && mvn quarkus:dev"
  exit 1
fi
echo ""

for script in "${SCRIPT_DIR}"/[0-9]*.sh; do
  bash "$script"
done

echo "══════════════════════════════════════════════════════════════"
echo "  Alle Tests abgeschlossen"
echo "══════════════════════════════════════════════════════════════"
