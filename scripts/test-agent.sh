#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-common.sh"

echo "=== RAG Test: Full Agent ==="

PASS=0; FAIL=0

run_test() {
    local label="$1" question="$2" expected_abstained="$3"
    local payload="{\"question\":$(echo "$question" | jq -R .)}"
    local resp answer actual

    echo "---"
    echo "[$label] — Question: $question"

    if resp=$(api_request POST "/api/v1/rag/query" "$payload"); then
        answer=$(echo "$resp" | jq -r '.answer // "null"')
        actual=$(echo "$resp" | jq -r '.abstained // "null"')
        echo "  Answer: ${answer:0:120}..."
        echo "  Abstained: $actual"

        if [[ "$actual" == "$expected_abstained" ]]; then
            ok "$label — abstained=${actual} (expected)"
            (( PASS++ ))
        else
            warn "$label — expected abstained=${expected_abstained}, got ${actual}"
            (( FAIL++ ))
        fi
    else
        warn "$label — non-2xx response"
        (( FAIL++ ))
    fi
}

echo ""
echo "--- Scenario 1: Simple question ---"
run_test "SIMPLE" "Who directed Doctor Strange?" false

echo ""
echo "--- Scenario 2: Comparison question ---"
run_test "COMPARISON" "Compare Scott Derrickson and Ed Wood" false

echo ""
echo "--- Scenario 3: Multi-hop question ---"
run_test "MULTI_HOP" "Who directed The Black Phone and what else did they direct?" false

echo ""
echo "--- Scenario 4: False premise (should abstain) ---"
run_test "FALSE_PREMISE" "What is the capital of Atlantis?" true

echo ""
echo "=============================="
echo -e "Results: ${GREEN}${PASS} passed${NC}, ${RED}${FAIL} failed${NC}"
exit $FAIL
