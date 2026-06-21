package de.augmentia.rag.domain;

import java.time.LocalDateTime;

public record GraphNode(
    String id,
    String chunkId,
    String entityName,
    String entityType,
    String description,
    String embedding,
    LocalDateTime createdAt
) {}
