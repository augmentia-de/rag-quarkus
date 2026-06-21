package de.augmentia.rag.domain;

public record AtomicClaim(
    String statement,
    String citedChunkId
) {}