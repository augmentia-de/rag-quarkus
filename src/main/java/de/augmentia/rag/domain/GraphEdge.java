package de.augmentia.rag.domain;

import java.time.LocalDateTime;

public record GraphEdge(
    String id,
    String sourceNodeId,
    String targetNodeId,
    String relationType,
    Float weight,
    String description,
    LocalDateTime createdAt
) {}
