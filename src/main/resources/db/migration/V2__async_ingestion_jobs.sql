CREATE TABLE rag_ingestion_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    total_chunks INT NOT NULL DEFAULT 0,
    processed_chunks INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rag_ingestion_jobs_status ON rag_ingestion_jobs(status);
