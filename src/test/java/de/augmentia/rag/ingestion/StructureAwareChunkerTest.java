package de.augmentia.rag.ingestion;

import de.augmentia.rag.domain.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructureAwareChunkerTest {

    private final StructureAwareChunker chunker = new StructureAwareChunker();

    @Test
    void splitsSingleSentenceIntoOneChunk() {
        var source = new Chunk("1", "d1", "Doc", "Hello world.", "Hello world.", List.of());
        var chunks = chunker.chunk(source);

        assertEquals(1, chunks.size());
        assertEquals("Hello world.", chunks.get(0).text());
    }

    @Test
    void splitsMultipleSentences() {
        var source = new Chunk("1", "d1", "Doc",
            "First sentence. Second sentence. Third sentence.",
            "First sentence. Second sentence. Third sentence.", List.of());
        var chunks = chunker.chunk(source);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().contains("First sentence"));
        assertTrue(chunks.get(0).text().contains("Third sentence"));
    }

    @Test
    void handlesTextWithoutPunctuation() {
        var source = new Chunk("1", "d1", "Doc", "A long text without punctuation marks to split on", "", List.of());
        var chunks = chunker.chunk(source);

        assertEquals(1, chunks.size());
    }

    @Test
    void chunkIdIsDeterministic() {
        var source = new Chunk("1", "d1", "Doc", "Hello world.", "Hello world.", List.of());
        var chunks1 = chunker.chunk(source);
        var chunks2 = chunker.chunk(source);

        assertEquals(chunks1.get(0).id(), chunks2.get(0).id());
    }

    @Test
    void preservesDocIdAndTitleInChunks() {
        var source = new Chunk("1", "d1", "Doc Title", "Some text. More text.", "Some text. More text.", List.of());
        var chunks = chunker.chunk(source);

        assertEquals("d1", chunks.get(0).docId());
        assertEquals("Doc Title", chunks.get(0).title());
    }

    @Test
    void handlesEmptyText() {
        var source = new Chunk("1", "d1", "Doc", "", "", List.of());
        var chunks = chunker.chunk(source);

        assertEquals(1, chunks.size());
    }
}