package de.augmentia.rag.repository;

import de.augmentia.rag.domain.Chunk;
import jakarta.persistence.*;

@Entity
@Table(name = "rag_chunks", indexes = {
    @Index(name = "idx_chunk_doc_id", columnList = "docId"),
    @Index(name = "idx_chunk_title", columnList = "title")
})
public class ChunkEntity {

    @Id
    @Column(length = 128)
    public String id;

    @Column(name = "doc_id", length = 128)
    public String docId;

    @Column(length = 512)
    public String title;

    @Column(columnDefinition = "TEXT")
    public String text;

    @Column(name = "contextual_text", columnDefinition = "TEXT")
    public String contextualText;

    @Column(name = "tsv", columnDefinition = "tsvector", insertable = false, updatable = false)
    public String tsv;

    @Column(name = "token_count")
    public int tokenCount;

    @Column(name = "gold_for_qids", columnDefinition = "TEXT")
    public String goldForQuestionIds;

    @Column(name = "graph_extracted")
    public boolean graphExtracted;

    public static ChunkEntity fromDomain(Chunk chunk) {
        var e = new ChunkEntity();
        e.id = chunk.id();
        e.docId = chunk.docId();
        e.title = chunk.title();
        e.text = chunk.text();
        e.contextualText = chunk.contextualText();
        e.goldForQuestionIds = String.join(",", chunk.goldForQuestionIds());
        return e;
    }

    public Chunk toDomain() {
        return new Chunk(
            id, docId, title, text, contextualText,
            goldForQuestionIds == null || goldForQuestionIds.isBlank()
                ? java.util.List.of()
                : java.util.Arrays.asList(goldForQuestionIds.split(","))
        );
    }
}