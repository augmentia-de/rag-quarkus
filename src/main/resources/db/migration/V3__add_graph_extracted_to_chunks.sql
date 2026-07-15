ALTER TABLE rag_chunks ADD COLUMN graph_extracted BOOLEAN DEFAULT FALSE;
CREATE INDEX idx_chunk_graph_extracted ON rag_chunks(graph_extracted);
