package de.augmentia.rag.domain;

import java.util.List;

public record GraphSearchResult(
    List<GraphNode> nodes,
    List<GraphEdge> edges,
    List<Chunk> contextChunks
) {}
