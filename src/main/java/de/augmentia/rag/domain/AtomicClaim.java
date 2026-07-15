package de.augmentia.rag.domain;

import java.util.Collections;
import java.util.List;

/**
 * A single verifiable statement extracted from a cited answer.
 *
 * @param statement claim text with citations stripped
 * @param citedChunkId primary cited chunk ID (first citation)
 * @param allCitedChunkIds all chunk IDs referenced in this claim
 */
public record AtomicClaim(
    String statement,
    String citedChunkId,
    List<String> allCitedChunkIds
) {
    public AtomicClaim(String statement, String citedChunkId) {
        this(statement, citedChunkId, Collections.singletonList(citedChunkId));
    }
}