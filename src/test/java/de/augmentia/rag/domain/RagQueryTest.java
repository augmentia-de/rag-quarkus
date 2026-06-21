package de.augmentia.rag.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RagQueryTest {

    @Test
    void factoryMethodCreatesDefaultQuery() {
        RagQuery query = RagQuery.of("Test question?");
        assertEquals("Test question?", query.question());
        assertNull(query.userId());
        assertEquals(20, query.topK());
        assertTrue(query.enableAbstention());
    }

    @Test
    void topKDefaultsTo20WhenZero() {
        var query = new RagQuery("question", null, 0, true);
        assertEquals(20, query.topK());
    }

    @Test
    void topKDefaultsTo20WhenNegative() {
        var query = new RagQuery("question", null, -5, true);
        assertEquals(20, query.topK());
    }

    @Test
    void preservesExplicitTopK() {
        var query = new RagQuery("question", "user-1", 50, false);
        assertEquals(50, query.topK());
        assertEquals("user-1", query.userId());
        assertFalse(query.enableAbstention());
    }
}