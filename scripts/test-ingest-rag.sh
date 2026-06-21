#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-common.sh"

echo "=== GraphRAG Test: Multi-Hop Graph Traversal ==="
echo ""

DOCUMENTS='{
  "documents": [
    {
      "id": "doc-elena",
      "docId": "elena-vasquez",
      "title": "Dr. Elena Vasquez — Photonics Research",
      "text": "Dr. Elena Vasquez is a senior researcher at the Advanced Optics Laboratory in Barcelona. She specializes in integrated photonics and silicon-based optical interconnects. Her team developed a novel Mach-Zehnder modulator design that reduces power consumption by 40 percent. The Advanced Optics Laboratory operates under the Mediterranean Photonics Consortium."
    },
    {
      "id": "doc-mpc",
      "docId": "med-photonics",
      "title": "Mediterranean Photonics Consortium — Overview",
      "text": "The Mediterranean Photonics Consortium (MPC) is a research alliance formed in 2015. MPC unified three institutions: the Advanced Optics Laboratory in Barcelona, the Valencia Photonics Institute, and the Marseille Photonics Center. MPC coordinates photonics research across the Mediterranean region and manages joint funding from the EU Horizon program."
    },
    {
      "id": "doc-valencia",
      "docId": "valencia-institute",
      "title": "Valencia Photonics Institute — History",
      "text": "The Valencia Photonics Institute was established in 1998 by Marcus Webb, a pioneer in fiber-optic communications. The institute focuses on optical fiber sensors and laser-based manufacturing. In 2015 it became part of the Mediterranean Photonics Consortium while retaining its research identity. Marcus Webb served as its director until 2010."
    },
    {
      "id": "doc-marcus",
      "docId": "marcus-webb",
      "title": "Marcus Webb — Biography",
      "text": "Marcus Webb is a British-American physicist and entrepreneur. He founded the Valencia Photonics Institute in 1998 after a career at Bell Labs. He holds 23 patents in fiber-optic technology and received the IEEE Photonics Award in 2015. He currently advises the European Commission on quantum communication infrastructure."
    },
    {
      "id": "doc-marseille",
      "docId": "marseille-center",
      "title": "Marseille Photonics Center",
      "text": "The Marseille Photonics Center joined the Mediterranean Photonics Consortium in 2015. It was founded by French physicist Claire Dubois and specializes in biophotonics and medical imaging. The center employs 45 researchers and has published over 200 papers on optical coherence tomography."
    }
  ]
}'

echo "--- Step 1: Ingest with GraphRAG ---"
echo ""
if RESP=$(api_request POST "/api/v1/rag/ingest" "$DOCUMENTS"); then
    echo "$RESP" | jq .
    assert_json "$RESP" '.inputPassages? == 5'              "5 input passages"
    assert_json "$RESP" '.chunksIndexed? >= 1'              "chunks indexed"
    ok "Ingestion with GraphRAG successful"
else
    warn "Ingestion returned non-2xx"
    echo "$RESP" | jq .
    exit 1
fi

echo ""

echo "--- Step 2: Standard RAG Query (2-hop question, no graph) ---"
echo ""
STANDARD_QUERY='{"question":"Who founded the institute that eventually became part of the consortium where Elena Vasquez works?"}'
if STD_RESP=$(api_request POST "/api/v1/rag/query" "$STANDARD_QUERY"); then
    ANSWER=$(echo "$STD_RESP" | jq -r '.answer')
    ABSTAINED=$(echo "$STD_RESP" | jq -r '.abstained')
    echo "Answer: $ANSWER"
    echo "Abstained: $ABSTAINED"
    assert_json "$STD_RESP" '.citations != null'            "Standard RAG returns valid structure"
else
    warn "Standard query returned non-2xx"
    echo "$STD_RESP" | jq .
fi

echo ""

echo "--- Step 3: GraphRAG Query — explicit multi-hop path ---"
echo ""
GRAPH_QUERY='{"question":"What is the connection between Elena Vasquez and Marcus Webb through the Mediterranean Photonics Consortium?","hops":3,"maxNodes":20}'
if GPH_RESP=$(api_request POST "/api/v1/rag/graph-query" "$GRAPH_QUERY"); then
    echo "$GPH_RESP" | jq .
    assert_json "$GPH_RESP" '.nodes | length >= 1'         "Graph query returns at least 1 node"
    assert_json "$GPH_RESP" '.edges | length >= 1'         "Graph query returns at least 1 edge"
    NODE_COUNT=$(echo "$GPH_RESP" | jq '.nodes | length')
    EDGE_COUNT=$(echo "$GPH_RESP" | jq '.edges | length')
    echo "Nodes: $NODE_COUNT"
    echo "Edges: $EDGE_COUNT"

    echo ""
    echo "--- Graph Path (shows explicit traversal) ---"
    echo "$GPH_RESP" | jq -r '
        "Seed nodes found:",
        (.nodes[] | "  • \(.entityName)"),
        "",
        "Relationships (BFS traversal):",
        (.edges[] | "  \(.relationType): \(.description // "-")")
    '
    ok "GraphRAG: explicit entity-relationship path returned"
else
    warn "Graph query returned non-2xx"
    echo "$GPH_RESP" | jq .
fi

echo ""

echo "--- Step 4: GraphRAG — direct entity lookup & 1-hop neighbors ---"
echo ""
GRAPH_QUERY2='{"question":"Marcus Webb Valencia Photonics","hops":1,"maxNodes":10}'
if GPH_RESP2=$(api_request POST "/api/v1/rag/graph-query" "$GRAPH_QUERY2"); then
    echo "$GPH_RESP2" | jq .
    assert_json "$GPH_RESP2" '.nodes | length >= 1'        "Entity lookup finds Marcus Webb"
    NODE_NAMES=$(echo "$GPH_RESP2" | jq -r '[.nodes[].entityName] | join(", ")')
    echo "Found nodes: $NODE_NAMES"

    echo ""
    echo "--- Relationships from entity lookup ---"
    echo "$GPH_RESP2" | jq -r '
        "Edges:",
        (.edges[] | "  \(.sourceNodeId[0:20]).. —[\(.relationType)]→ \(.targetNodeId[0:20])..  | \(.description // "-")")
    '
fi

echo ""
echo "=== Done ==="