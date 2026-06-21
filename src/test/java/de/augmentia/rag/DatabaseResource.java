package de.augmentia.rag;

import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.ResourceReaper;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class DatabaseResource implements QuarkusTestResourceLifecycleManager {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
        .withDatabaseName("rag")
        .withUsername("rag")
        .withPassword("rag");

    @Override
    public Map<String, String> start() {
        postgres.start();

        try (var conn = postgres.createConnection("");
             var s = conn.createStatement()) {
            s.execute("CREATE EXTENSION IF NOT EXISTS vector");
            s.execute("""
                CREATE TABLE IF NOT EXISTS rag_ingestion_jobs (
                    id UUID PRIMARY KEY,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    total_chunks INT NOT NULL DEFAULT 0,
                    processed_chunks INT NOT NULL DEFAULT 0,
                    error_message TEXT,
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                )
            """);
            s.execute("""
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
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS graph_nodes (
                    id            VARCHAR(128) PRIMARY KEY,
                    chunk_id      VARCHAR(128) REFERENCES rag_chunks(id) ON DELETE SET NULL,
                    entity_name   VARCHAR(512) NOT NULL,
                    entity_type   VARCHAR(128),
                    description   TEXT,
                    embedding     vector(1024),
                    created_at    TIMESTAMP DEFAULT now()
                )
            """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS graph_edges (
                    id              VARCHAR(128) PRIMARY KEY,
                    source_node_id  VARCHAR(128) REFERENCES graph_nodes(id) ON DELETE CASCADE,
                    target_node_id  VARCHAR(128) REFERENCES graph_nodes(id) ON DELETE CASCADE,
                    relation_type   VARCHAR(256) NOT NULL,
                    weight          FLOAT DEFAULT 1.0,
                    description     TEXT,
                    created_at      TIMESTAMP DEFAULT now()
                )
            """);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }

        return Map.of(
            "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
            "quarkus.datasource.username", postgres.getUsername(),
            "quarkus.datasource.password", postgres.getPassword(),
            "rag.auth.api-key", "test-key"
        );
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }
}
