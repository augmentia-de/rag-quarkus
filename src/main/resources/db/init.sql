CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS rag_chunks (
    id          VARCHAR(128) PRIMARY KEY,
    doc_id      VARCHAR(128),
    title       VARCHAR(512),
    text        TEXT NOT NULL,
    contextual_text TEXT,
    embedding   vector(1024),
    tsv         tsvector GENERATED ALWAYS AS (
                    to_tsvector('english', coalesce(contextual_text, text))
                ) STORED,
    token_count INT DEFAULT 0,
    gold_for_qids TEXT
);

CREATE INDEX IF NOT EXISTS idx_chunk_doc_id ON rag_chunks(doc_id);
CREATE INDEX IF NOT EXISTS idx_chunk_title ON rag_chunks(title);
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON rag_chunks
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_chunk_tsv ON rag_chunks USING gin(tsv);

CREATE TABLE IF NOT EXISTS graph_nodes (
    id            VARCHAR(128) PRIMARY KEY,
    chunk_id      VARCHAR(128) REFERENCES rag_chunks(id) ON DELETE SET NULL,
    entity_name   VARCHAR(512) NOT NULL,
    entity_type   VARCHAR(128),
    description   TEXT,
    embedding     vector(1024),
    created_at    TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS graph_edges (
    id              VARCHAR(128) PRIMARY KEY,
    source_node_id  VARCHAR(128) REFERENCES graph_nodes(id) ON DELETE CASCADE,
    target_node_id  VARCHAR(128) REFERENCES graph_nodes(id) ON DELETE CASCADE,
    relation_type   VARCHAR(256) NOT NULL,
    weight          FLOAT DEFAULT 1.0,
    description     TEXT,
    created_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_graph_nodes_chunk_id ON graph_nodes(chunk_id);
CREATE INDEX IF NOT EXISTS idx_graph_nodes_entity_name ON graph_nodes(entity_name);
CREATE INDEX IF NOT EXISTS idx_graph_nodes_embedding ON graph_nodes
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_graph_edges_source ON graph_edges(source_node_id);
CREATE INDEX IF NOT EXISTS idx_graph_edges_target ON graph_edges(target_node_id);
CREATE INDEX IF NOT EXISTS idx_graph_edges_relation ON graph_edges(relation_type);
