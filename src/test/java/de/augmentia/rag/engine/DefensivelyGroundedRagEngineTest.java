package de.augmentia.rag.engine;

import de.augmentia.rag.domain.AtomicClaim;
import de.augmentia.rag.repository.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefensivelyGroundedRagEngineTest {

    private final ClaimDecomposer claimDecomposer = new ClaimDecomposer();
    private final ReciprocalRankFusion rrf = new ReciprocalRankFusion();

    @Test
    void testRRFFusion() {
        var dense = List.of(
            new SearchResult("c", 0.9f),
            new SearchResult("a", 0.8f),
            new SearchResult("b", 0.7f)
        );
        var sparse = List.of(
            new SearchResult("b", 0.6f),
            new SearchResult("c", 0.5f),
            new SearchResult("a", 0.4f)
        );

        List<String> fused = rrf.fuse(dense, sparse);
        assertEquals("c", fused.get(0));
        assertEquals("b", fused.get(1));
        assertEquals("a", fused.get(2));
    }

    @Test
    void testClaimDecomposition() {
        String answer = "Scott Derrickson is an American director. [ID: p1] " +
            "Ed Wood was also American. [ID: p2]";

        List<AtomicClaim> claims = claimDecomposer.decompose(answer);
        assertEquals(2, claims.size());
        assertEquals("p1", claims.get(0).citedChunkId());
        assertEquals("Scott Derrickson is an American director.", claims.get(0).statement());
        assertEquals("p2", claims.get(1).citedChunkId());
    }
}