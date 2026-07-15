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
    },
    {
      "id": "doc-comparison",
      "docId": "houdini-vs-chaplin",
      "title": "Houdini vs Chaplin Comparison",
      "text": "Harry Houdini was a Hungarian-American escape artist and magician who lived from 1874 to 1926. Charlie Chaplin was an English-American actor and director who lived from 1889 to 1977. Both were early 20th century entertainment icons. Houdini focused on stunts while Chaplin focused on comedy films. Houdini died young from peritonitis while Chaplin lived to age 88."
    },
    {
      "id": "doc-quantum",
      "docId": "quantum-computing",
      "title": "Quantum Computing Fundamentals",
      "text": "Quantum computing uses quantum bits or qubits that can exist in superposition. Unlike classical bits, qubits can be both 0 and 1 simultaneously. This allows quantum computers to solve certain problems exponentially faster. IBM Quantum and Google Quantum are leading commercial efforts. Quantum computers require extreme cooling near absolute zero."
    },
    {
      "id": "doc-paris",
      "docId": "paris-france",
      "title": "Paris, France",
      "text": "Paris is the capital city of France. It is located in the north-central part of the country along the Seine river. Paris is known as the City of Light and is famous for the Eiffel Tower. The population of Paris is about 2.1 million people. The currency used in France is the Euro."
    },
    {
      "id": "doc-python",
      "docId": "python-lang",
      "title": "Python Programming Language",
      "text": "Python was created by Guido van Rossum in 1991. It is a high-level interpreted programming language known for readability. Python uses significant whitespace and has a simple syntax. Major versions include Python 2 and Python 3. Python is widely used in data science, web development, and AI applications."
    },
    {
      "id": "doc-java",
      "docId": "java-lang",
      "title": "Java Programming Language",
      "text": "Java was created by James Gosling at Sun Microsystems in 1995. Java is an object-oriented programming language that runs on the JVM. It is known for write once, run anywhere capability. Java uses strong type checking and automatic garbage collection. Spring Boot is a popular Java framework for web applications."
    }
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
