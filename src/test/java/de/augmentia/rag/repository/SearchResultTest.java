package de.augmentia.rag.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchResultTest {

    @Test
    void createsWithConstructor() {
        var sr = new SearchResult("chunk-1", 0.85f);
        assertEquals("chunk-1", sr.getChunkId());
        assertEquals(0.85f, sr.getScore());
    }

    @Test
    void createsWithNoArgsConstructor() {
        var sr = new SearchResult();
        assertNull(sr.getChunkId());
        assertEquals(0.0f, sr.getScore());
    }

    @Test
    void settersWork() {
        var sr = new SearchResult();
        sr.setChunkId("chunk-2");
        sr.setScore(0.95f);
        assertEquals("chunk-2", sr.getChunkId());
        assertEquals(0.95f, sr.getScore());
    }
}