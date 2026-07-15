#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# 07 — Graph-Enhanced Query (GraphRAG)
# ═══════════════════════════════════════════════════════════════
#
# Beschreibung:
#   Nutzt den extrahierten Wissensgraphen für die Suche.
#   Statt nur Chunks zu durchsuchen, werden Entitäten und
#   Beziehungen im Graphen traversiert.
#
# Voraussetzungen:
#   - 02-ingest.sh muss zuerst ausgeführt worden sein
#   - rag.graph.enabled=true (default)
#
# Erwartetes Ergebnis:
#   HTTP 200 mit JSON-Body der:
#     - "answer": LLM-Antwort basierend auf Graph-Kontext
#     - "nodes": Array von Entitäten aus dem Graphen
#     - "edges": Array von Beziehungen zwischen Entitäten
#
# Was passiert im Hintergrund:
#   1. Query-Embedding → Ähnlichste Entitäten im Graphen
#   2. Graph-Traversal (Hops) → Nachbar-Entitäten
#   3. Kontext aus zugehörigen Chunks
#   4. LLM generiert Antwort aus Graph-Kontext
# ═══════════════════════════════════════════════════════════════

set -euo pipefail
BASE="http://localhost:8086"
API_KEY="dev-key-change-me"

echo "── 07 Graph-Enhanced Query (GraphRAG) ──"
echo "POST ${BASE}/api/v1/rag/query"
echo "Frage: \"What is the relationship between Elon Musk and SpaceX?\""
echo ""

RESPONSE=$(curl -s -w '\n%{http_code}' \
  -X POST "${BASE}/api/v1/rag/graph-query" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{"question": "What is the relationship between Elon Musk and SpaceX?", "hops": 2, "maxNodes": 20}')

BODY=$(echo "$RESPONSE" | sed '$d')
STATUS=$(echo "$RESPONSE" | tail -1)

echo "HTTP Status: ${STATUS}"
echo ""

if [ "$STATUS" = "200" ]; then
  echo "Graphergebnisse:"
  echo "$BODY" | python3 -c "
import sys, json
data = json.load(sys.stdin)
nodes = data.get('nodes', [])
edges = data.get('edges', [])
contextChunks = data.get('contextChunks', [])
print(f'  Nodes: {len(nodes)} Entitäten')
for n in nodes[:5]:
    print(f'    [{n.get(\"entityName\",\"?\")}] ({n.get(\"entityType\",\"?\")})')
print(f'  Edges: {len(edges)} Beziehungen')
for e in edges[:5]:
    print(f'    {e.get(\"sourceNodeId\",\"?\")} --[{e.get(\"relationType\",\"?\")}]--> {e.get(\"targetNodeId\",\"?\")}')
print(f'  Context Chunks: {len(contextChunks)}')
" 2>/dev/null
  echo ""

  NODES=$(echo "$BODY" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('nodes',[])))" 2>/dev/null)
  if [ "$NODES" -gt 0 ]; then
    echo "✅ ERGEBNIS: Graph-Query liefert Knoten und Kanten"
  else
    echo "⚠️  ERGEBNIS: Graph-Query liefert keine Knoten (Graph evtl. leer)"
  fi
else
  echo "❌ ERGEBNIS: Graph-Query fehlgeschlagen (HTTP ${STATUS})"
fi
echo ""
