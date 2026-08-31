-- Heals deployments where rag_ingestion_jobs was skipped because Flyway
-- was previously baselined at version 2 (see application.properties
-- quarkus.flyway.baseline-version migration). Idempotent: a no-op wherever
-- V2__async_ingestion_jobs.sql already created the table.
CREATE TABLE IF NOT EXISTS rag_ingestion_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    total_chunks INT NOT NULL DEFAULT 0,
    processed_chunks INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_ingestion_jobs_status ON rag_ingestion_jobs(status);