package de.augmentia.rag.domain;

import java.util.List;

public record Chunk(
    String id,
    String docId,
    String title,
    String text,
    String contextualText,
    List<String> goldForQuestionIds
) {}