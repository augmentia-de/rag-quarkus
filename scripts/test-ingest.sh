#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/test-common.sh"

echo "=== RAG Test: Ingest Documents ==="

DOCUMENTS='{
  "documents": [
    {
      "id": "doc-scott",
      "docId": "scott-derrickson",
      "title": "Scott Derrickson -- Biography",
      "text": "Scott Derrickson is an American film director, screenwriter, and producer. He is best known for directing the Marvel Cinematic Universe film Doctor Strange. He also directed Sinister and The Black Phone. Derrickson was born in Denver, Colorado and studied film at Biola University."
    },
    {
      "id": "doc-ed-wood",
      "docId": "ed-wood",
      "title": "Ed Wood -- Biography",
      "text": "Edward D. Wood Jr. was an American film director, screenwriter, and actor. He is famous for low-budget cult films like Plan 9 from Outer Space. Wood has often been called the worst director of all time. Despite this, his work has gained a devoted following over the decades."
    },
    {
      "id": "doc-neil",
      "docId": "neil-armstrong",
      "title": "Neil Armstrong -- Biography",
      "text": "Neil Armstrong was an American astronaut and the first person to walk on the Moon. He was born in Wapakoneta, Ohio in 1930. Armstrong served as a naval aviator before joining NASA. He commanded the Apollo 11 mission in 1969."
    },
    {
      "id": "doc-apollo",
      "docId": "apollo-11",
      "title": "Apollo 11 -- Mission Overview",
      "text": "Apollo 11 was the spaceflight that first landed humans on the Moon. Commander Neil Armstrong and lunar module pilot Buzz Aldrin landed the Apollo Lunar Module Eagle on July 20, 1969. Armstrong became the first person to walk on the Moon. The mission fulfilled a national goal set by President John F. Kennedy in 1961."
    }
  ]
}'

if RESP=$(api_request POST "/api/v1/rag/ingest" "$DOCUMENTS"); then
    echo "$RESP" | jq .
    assert_json "$RESP" '.inputPassages? >= 1'            "inputPassages present"
    assert_json "$RESP" '.duplicatesRemoved? >= 0'        "duplicatesRemoved present"
    assert_json "$RESP" '.chunksIndexed? >= 1'            "chunks indexed"
    ok "Ingestion successful"
else
    warn "Ingestion returned non-2xx"
    exit 1
fi
