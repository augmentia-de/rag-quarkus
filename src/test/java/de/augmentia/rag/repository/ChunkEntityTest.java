package de.augmentia.rag.repository;

import de.augmentia.rag.domain.Chunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkEntityTest {

    @Test
    void convertsFromDomainToEntity() {
        var domain = new Chunk(
            "id-1", "doc-1", "Title", "Some text.", "Context: Some text.",
            java.util.List.of("q1", "q2")
        );

        var entity = ChunkEntity.fromDomain(domain);

        assertEquals("id-1", entity.id);
        assertEquals("doc-1", entity.docId);
        assertEquals("Title", entity.title);
        assertEquals("Some text.", entity.text);
        assertEquals("Context: Some text.", entity.contextualText);
        assertEquals("q1,q2", entity.goldForQuestionIds);
    }

    @Test
    void convertsFromEntityToDomain() {
        var entity = new ChunkEntity();
        entity.id = "id-1";
        entity.docId = "doc-1";
        entity.title = "Title";
        entity.text = "Some text.";
        entity.contextualText = "Context: Some text.";
        entity.goldForQuestionIds = "q1,q2";

        var domain = entity.toDomain();

        assertEquals("id-1", domain.id());
        assertEquals("doc-1", domain.docId());
        assertEquals("Title", domain.title());
        assertEquals("Some text.", domain.text());
        assertEquals("Context: Some text.", domain.contextualText());
        assertEquals(2, domain.goldForQuestionIds().size());
    }

    @Test
    void handlesNullGoldForQuestionIds() {
        var entity = new ChunkEntity();
        entity.id = "id";
        entity.docId = "doc";
        entity.title = "Title";
        entity.text = "text";
        entity.contextualText = "context";
        entity.goldForQuestionIds = null;

        var domain = entity.toDomain();
        assertTrue(domain.goldForQuestionIds().isEmpty());
    }

    @Test
    void handlesBlankGoldForQuestionIds() {
        var entity = new ChunkEntity();
        entity.id = "id";
        entity.docId = "doc";
        entity.title = "Title";
        entity.text = "text";
        entity.contextualText = "context";
        entity.goldForQuestionIds = "   ";

        var domain = entity.toDomain();
        assertTrue(domain.goldForQuestionIds().isEmpty());
    }

    @Test
    void fromDomainWithEmptyGoldForQuestionIds() {
        var domain = new Chunk(
            "id", "doc", "Title", "text", "context", java.util.List.of()
        );

        var entity = ChunkEntity.fromDomain(domain);
        assertEquals("", entity.goldForQuestionIds);
    }
}