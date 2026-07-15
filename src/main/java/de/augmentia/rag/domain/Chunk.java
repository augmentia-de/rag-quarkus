package de.augmentia.rag.domain;

import java.util.List;

/**
 * Document chunk — the atomic unit of retrieval.
 *
 * @param id deterministic UUID from content hash
 * @param docId parent document ID
 * @param title document title
 * @param text original chunk text
 * @param contextualText LLM-enriched text with document context (used for embedding)
 * @param goldForQuestionIds evaluation ground truth question IDs
 */
public record Chunk(
    String id,
    String docId,
    String title,
    String text,
    String contextualText,
    List<String> goldForQuestionIds
) {}