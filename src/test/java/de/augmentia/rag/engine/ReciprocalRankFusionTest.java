package de.augmentia.rag.engine;

import de.augmentia.rag.repository.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReciprocalRankFusionTest {

    private final ReciprocalRankFusion rrf = new ReciprocalRankFusion();

    @Test
    void fusionReturnsEmptyForEmptyInputs() {
        assertTrue(rrf.fuse(List.of(), List.of()).isEmpty());
    }

    @Test
    void documentInBothListsRanksHighest() {
        var dense = List.of(
            new SearchResult("a", 0.9f),
            new SearchResult("b", 0.8f),
            new SearchResult("c", 0.7f)
        );
        var sparse = List.of(
            new SearchResult("b", 0.6f),
            new SearchResult("c", 0.5f),
            new SearchResult("a", 0.3f)
        );

        List<String> fused = rrf.fuse(dense, sparse);
        assertEquals("b", fused.get(0));
        assertEquals("a", fused.get(1));
        assertEquals("c", fused.get(2));
    }

    @Test
    void idInOnlyOneListStillAppears() {
        var dense = List.of(
            new SearchResult("a", 0.9f),
            new SearchResult("b", 0.8f)
        );
        var sparse = List.of(
            new SearchResult("c", 0.7f)
        );

        List<String> fused = rrf.fuse(dense, sparse);
        assertTrue(fused.contains("a"));
        assertTrue(fused.contains("b"));
        assertTrue(fused.contains("c"));
        assertEquals(3, fused.size());
    }

    @Test
    void differentKChangesRankings() {
        var dense = List.of(
            new SearchResult("a", 0.9f),
            new SearchResult("b", 0.8f)
        );
        var sparse = List.of(
            new SearchResult("b", 0.7f),
            new SearchResult("a", 0.6f)
        );

        List<String> smallK = rrf.fuse(dense, sparse, 1);
        List<String> largeK = rrf.fuse(dense, sparse, 100);

        assertEquals(2, smallK.size());
        assertEquals(2, largeK.size());
    }

    @Test
    void sameIdInBothListsGetsDoubleScore() {
        var dense = List.of(new SearchResult("x", 1.0f));
        var sparse = List.of(new SearchResult("x", 1.0f));

        List<String> fused = rrf.fuse(dense, sparse, 60);
        assertEquals(1, fused.size());
        assertEquals("x", fused.getFirst());
    }
}