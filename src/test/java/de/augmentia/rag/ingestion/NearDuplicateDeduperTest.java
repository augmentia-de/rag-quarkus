package de.augmentia.rag.ingestion;

import de.augmentia.rag.domain.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NearDuplicateDeduperTest {

    private final NearDuplicateDeduper deduper = new NearDuplicateDeduper();

    @Test
    void keepsUniqueDocuments() {
        var chunks = List.of(
            new Chunk("1", "d1", "Doc A", "The quick brown fox jumps over the lazy dog.", "", List.of()),
            new Chunk("2", "d2", "Doc B", "Completely different content here.", "", List.of())
        );

        var result = deduper.deduplicate(chunks);
        assertEquals(2, result.kept().size());
        assertEquals(0, result.droppedCount());
    }

    @Test
    void dropsExactDuplicates() {
        var chunks = List.of(
            new Chunk("1", "d1", "Doc", "This is a test document with enough words to make a proper shingle set.", "", List.of()),
            new Chunk("2", "d2", "Doc", "This is a test document with enough words to make a proper shingle set.", "", List.of())
        );

        var result = deduper.deduplicate(chunks);
        assertEquals(1, result.kept().size());
        assertEquals(1, result.droppedCount());
    }

    @Test
    void emptyInputReturnsEmpty() {
        var result = deduper.deduplicate(List.of());
        assertTrue(result.kept().isEmpty());
        assertEquals(0, result.droppedCount());
    }

    @Test
    void singleDocumentIsKept() {
        var chunks = List.of(
            new Chunk("1", "d1", "Doc", "Some content here for testing.", "", List.of())
        );

        var result = deduper.deduplicate(chunks);
        assertEquals(1, result.kept().size());
        assertEquals(0, result.droppedCount());
    }

    @Test
    void keepsSlightlyDifferentDocuments() {
        var chunks = List.of(
            new Chunk("1", "d1", "Doc A", "Python is a programming language that is widely used.", "", List.of()),
            new Chunk("2", "d2", "Doc B", "Java is a completely different programming language.", "", List.of())
        );

        var result = deduper.deduplicate(chunks);
        assertEquals(2, result.kept().size());
    }
}