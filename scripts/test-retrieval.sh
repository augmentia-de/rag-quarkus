#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-common.sh"

echo "=== RAG Test: Retrieval ==="

QUESTION='{"question":"Who directed Doctor Strange?"}'
if RESP=$(api_request POST "/api/v1/rag/query" "$QUESTION"); then
    echo "$RESP" | jq .
    assert_json "$RESP" '.answer? | length > 0'               "answer field present and non-empty"
    assert_json "$RESP" 'has("abstained")'                     "abstained field present"
    assert_json "$RESP" 'has("verification")'                  "verification field present"

    ABSTAINED=$(echo "$RESP" | jq -r '.abstained')
    if [[ "$ABSTAINED" == "true" ]]; then
        warn "Answer was ABSTAINED (LLM response may lack citations)"
    else
        CITATIONS=$(echo "$RESP" | jq -r '.citations | length')
        ok "Citations count: $CITATIONS"
    fi
    ok "Retrieval test complete"
else
    warn "Query returned non-2xx"
    exit 1
fi
