package de.augmentia.rag.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkTest {

    @Test
    void createsChunkWithAllFields() {
        var chunk = new Chunk("id-1", "doc-1", "Title", "Some text.", "Context: Some text.",
            List.of("q1", "q2"));

        assertEquals("id-1", chunk.id());
        assertEquals("doc-1", chunk.docId());
        assertEquals("Title", chunk.title());
        assertEquals("Some text.", chunk.text());
        assertEquals("Context: Some text.", chunk.contextualText());
        assertEquals(2, chunk.goldForQuestionIds().size());
    }

    @Test
    void chunksWithSameFieldsAreEqual() {
        var c1 = new Chunk("id", "doc", "Title", "text", "context", List.of("q1"));
        var c2 = new Chunk("id", "doc", "Title", "text", "context", List.of("q1"));

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void chunksWithDifferentIdsAreNotEqual() {
        var c1 = new Chunk("id-1", "doc", "Title", "text", "context", List.of());
        var c2 = new Chunk("id-2", "doc", "Title", "text", "context", List.of());

        assertNotEquals(c1, c2);
    }

    @Test
    void handlesEmptyGoldForQuestionIds() {
        var chunk = new Chunk("id", "doc", "Title", "text", "context", List.of());
        assertTrue(chunk.goldForQuestionIds().isEmpty());
    }
}