package de.augmentia.rag.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetrievedChunkTest {

    @Test
    void createsWithAllFields() {
        var chunk = new Chunk("id", "doc", "Title", "text", "context", java.util.List.of());
        var rc = new RetrievedChunk(chunk, 0.9, 0.7, 0.05, 0.85);

        assertEquals("id", rc.chunk().id());
        assertEquals(0.9, rc.denseScore());
        assertEquals(0.7, rc.sparseScore());
        assertEquals(0.05, rc.rrfScore());
        assertEquals(0.85, rc.rerankScore());
    }

    @Test
    void withRerankScoreReturnsNewInstance() {
        var chunk = new Chunk("id", "doc", "Title", "text", "context", java.util.List.of());
        var rc = new RetrievedChunk(chunk, 0.9, 0.7, 0.05, 0.0);

        var updated = rc.withRerankScore(0.95);
        assertEquals(0.95, updated.rerankScore());
        assertEquals(0.0, rc.rerankScore()); // original unchanged
    }
}