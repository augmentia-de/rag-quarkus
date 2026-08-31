CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_graph_nodes_entity_name_trgm
    ON graph_nodes USING gin (entity_name gin_trgm_ops);