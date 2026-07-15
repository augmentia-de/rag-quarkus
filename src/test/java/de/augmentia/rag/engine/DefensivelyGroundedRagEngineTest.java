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

    @Test
    void similarityThreshold_filtersLowScores() {
        var results = List.of(
            new SearchResult("a", 0.95f),
            new SearchResult("b", 0.80f),
            new SearchResult("c", 0.60f),
            new SearchResult("d", 0.40f),
            new SearchResult("e", 0.20f)
        );

        double maxScore = results.stream().mapToDouble(SearchResult::getScore).max().orElse(0.0);
        double ratio = 0.6;
        double threshold = maxScore * ratio;

        var filtered = results.stream()
            .filter(r -> r.getScore() >= threshold)
            .toList();

        assertEquals(0.57, threshold, 0.001);
        assertEquals(3, filtered.size());
        assertEquals("a", filtered.get(0).getChunkId());
        assertEquals("b", filtered.get(1).getChunkId());
        assertEquals("c", filtered.get(2).getChunkId());
    }

    @Test
    void similarityThreshold_emptyResults() {
        var results = List.<SearchResult>of();

        double maxScore = results.stream().mapToDouble(SearchResult::getScore).max().orElse(0.0);
        double threshold = maxScore * 0.6;

        assertEquals(0.0, threshold, 0.001);
    }

    @Test
    void similarityThreshold_singleResult() {
        var results = List.of(new SearchResult("a", 0.7f));

        double maxScore = results.stream().mapToDouble(SearchResult::getScore).max().orElse(0.0);
        double threshold = maxScore * 0.6;

        var filtered = results.stream()
            .filter(r -> r.getScore() >= threshold)
            .toList();

        assertEquals(1, filtered.size());
    }

    @Test
    void effectiveTopK_limitsToChunkCount() {
        int configuredTopK = 150;
        int totalChunks = 10;

        int effectiveTopK = Math.min(configuredTopK, Math.max(1, totalChunks));

        assertEquals(10, effectiveTopK);
    }

    @Test
    void effectiveTopK_usesConfiguredWhenMoreChunks() {
        int configuredTopK = 150;
        int totalChunks = 500;

        int effectiveTopK = Math.min(configuredTopK, Math.max(1, totalChunks));

        assertEquals(150, effectiveTopK);
    }

    @Test
    void effectiveTopK_minimumOne() {
        int configuredTopK = 150;
        int totalChunks = 0;

        int effectiveTopK = Math.min(configuredTopK, Math.max(1, totalChunks));

        assertEquals(1, effectiveTopK);
    }
}