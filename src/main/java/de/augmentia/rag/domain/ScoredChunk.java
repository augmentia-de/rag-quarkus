package de.augmentia.rag.domain;

/**
 * A chunk with its retrieval similarity score (0.0-1.0).
 *
 * @param id chunk ID
 * @param docId parent document ID
 * @param title document title
 * @param text original chunk text
 * @param contextualText LLM-enriched text with document context
 * @param score cosine similarity from vector search
 */
public record ScoredChunk(
    String id,
    String docId,
    String title,
    String text,
    String contextualText,
    double score
) {
    /** Convenience constructor from Chunk + score. */
    public ScoredChunk(Chunk chunk, double score) {
        this(chunk.id(), chunk.docId(), chunk.title(), chunk.text(), chunk.contextualText(), score);
    }
}
