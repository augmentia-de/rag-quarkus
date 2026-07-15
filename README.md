# RAG-Quarkus – Production-Grade RAG Engine

A **near-zero hallucination** RAG pipeline on **Quarkus + PostgreSQL (pgvector)** implementing the 4-layer defensive architecture: **Retrieve → Constrain → Verify → Abstain**.

## Architecture

```
User Query → [Query Router / Decomposer]
                  │
                  ▼ (Hybrid Retrieval)
      [pgvector (cosine)] + [PostgreSQL tsvector (BM25)]
                  │
                  ▼
   [Reciprocal Rank Fusion (RRF)] → [Cross-Encoder Reranker] ───┐
                  │                                                │
                  ▼ (Strict Prompt Isolation)                      │ (GraphRAG)
            [Cited Content Generation]                            │
                  │                                                │
                  ▼                                                ▼
      [Knowledge Graph Traversal] ──────────────────── [Graph-Augmented Context]
       (BFS over graph_nodes / graph_edges)                     │
                  │                                                │
                  └───────────────┬────────────────────────────────┘
                                  ▼
                    [Atomic Claim Decomposition]
                                  │
                                  ▼
                    [NLI Faithfulness Gate]
                                  │
                        ┌─────────┴─────────┐
                        ▼ (Pass)            ▼ (Fail)
                  [Final Response]      [CRAG Loop / Abstain]
```

## Quickstart

### Option A: Existing infrastructure (Ollama + PostgreSQL container)

```bash
# 1. Verify dependencies are running
docker ps | grep postgres-vector     # PostgreSQL with pgvector on port 5432
curl http://localhost:11434/api/tags # Ollama on port 11434

# 2. Run the automated setup
./scripts/test-setup.sh              # creates DB, builds app, starts Quarkus

# 3. Ingest sample documents
./scripts/test-ingest.sh

# 4. Query
curl -X POST http://localhost:8086/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-key-change-me" \
  -d '{"question":"Who directed Doctor Strange?"}'
```

### Option B: docker-compose (standalone)

```bash
docker compose up -d
```

## API

| Endpoint | Method | Description |
|---|---|---|
| `/api/v1/rag/query` | POST | Answer a question with cited evidence |
| `/api/v1/rag/graph-query` | POST | GraphRAG query — traverses knowledge graph |
| `/api/v1/rag/ingest` | POST | Ingest documents into the index |
| `/api/v1/rag/retrieve` | POST | Retrieve chunks with similarity scores (no LLM) |
| `/api/v1/rag/fetch-url` | POST | Fetch a URL and return its text content |
| `/q/health` | GET | Health check (liveness + readiness) |
| `/q/metrics` | GET | Micrometer metrics |
| `/q/openapi` | GET | OpenAPI spec |

## Key Features

- **Hybrid Search**: pgvector (cosine similarity) + PostgreSQL `ts_rank` fused via RRF
- **Cosine Similarity Reranking**: Embedding-based reranking on top-150 candidates
- **Similarity Score Reporting**: Relative dense threshold filtering (`rag.retrieve.similarity-ratio`) with scores returned via `/retrieve`
- **Cited Generation**: Every sentence cites its source `[ID: xx]` or the model emits `ABSTAIN`
- **Faithfulness Gate**: Atomic claim decomposition + LLM-as-judge NLI verification
- **CRAG Loop**: Self-correcting retrieval up to 3 hops on weak evidence
- **Query Routing**: Classifies SIMPLE / COMPARISON / MULTI_HOP / FALSE_PREMISE
- **Structure-Aware Chunking**: Sentence-boundary chunking with contextual prefix
- **Near-Duplicate Dedup**: MinHash LSH during ingestion
- **GraphRAG (Knowledge Graph)**: Entity + relation extraction during ingestion, BFS graph traversal with pgvector for relationship-aware queries
- **LLM Logging**: Optional request/response logging for debugging (`rag.llm.logging.enabled`)

## Configuration

Key properties in `application.properties`:

| Property | Default | Description |
|---|---|---|
| `rag.llm.model` | `meta-llama/llama-3.3-70b-instruct:free` | Generator model |
| `rag.embedding.model` | `mxbai-embed-large:latest` | Embedding model |
| `rag.retrieve.top-k` | `50` | Initial retrieval count (dynamic: capped at available chunks) |
| `rag.retrieve.rrf-k` | `30` | RRF constant |
| `rag.retrieve.similarity-ratio` | `0.6` | Relative dense threshold (score >= maxScore × ratio) |
| `rag.rerank.top-n` | `20` | Final context count |
| `rag.judge.tau-claim` | `0.01` | Faithfulness threshold |
| `rag.crag.max-hops` | `3` | CRAG loop limit |
| `rag.graph.enabled` | `false` | Enable GraphRAG augmentation |
| `rag.graph.hops` | `2` | BFS traversal depth for graph queries |
| `rag.graph.max-nodes` | `20` | Maximum nodes in graph subgraph |
| `rag.graph.extraction.max-triples-per-chunk` | `5` | Entity triples extracted per chunk |
| `rag.graph.extraction.temperature` | `0.0` | LLM temperature for graph extraction |
| `rag.llm.logging.enabled` | `false` | Enable LLM request/response logging |
| `rag.llm.logging.max-content-length` | `1000` | Max content length logged |

## Project Structure

```
src/main/java/de/augmentia/rag/
├── domain/          # Records: Chunk, ScoredChunk, RagQuery, RagResponse, AtomicClaim, GraphNode, GraphEdge, ...
├── repository/      # JPA entities + pgvector/tsvector native queries + graph repos
├── engine/          # Core pipeline: RRF, Reranker, Judge, Router, GraphSearchService
├── ingestion/       # Cleaner, Deduper, Chunker, Contextualizer
├── ai/              # LangChain4j AI services: Generator, Embedding, Judge, GraphExtractor, LlmLogger
├── rest/            # REST resources (versioned: /api/v1)
├── config/          # Configuration validation + monitoring
├── auth/            # API key authentication
└── mcp/             # MCP tools for external integrations
```

## Stack

- **Quarkus 3.36** – Reactive runtime with GraalVM native support
- **PostgreSQL 16 + pgvector** – Hybrid vector + full-text index
- **LangChain4j 1.11** – Declarative LLM clients
- **SmallRye Fault Tolerance** – Circuit breakers for LLM calls
- **Micrometer + OpenTelemetry** – Metrics and distributed tracing
- **Quarkus Cache** – Response caching
