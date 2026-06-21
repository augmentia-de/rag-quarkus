package de.augmentia.rag.engine;

import de.augmentia.rag.domain.AtomicClaim;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClaimDecomposerTest {

    private final ClaimDecomposer decomposer = new ClaimDecomposer();

    @Test
    void decomposeSimpleAnswer() {
        String answer = "Scott Derrickson is an American director. [ID: p1]";
        List<AtomicClaim> claims = decomposer.decompose(answer);

        assertEquals(1, claims.size());
        assertEquals("Scott Derrickson is an American director.", claims.get(0).statement());
        assertEquals("p1", claims.get(0).citedChunkId());
    }

    @Test
    void decomposeMultipleSentences() {
        String answer = "Scott Derrickson is an American director. [ID: p1] " +
            "Ed Wood was also American. [ID: p2]";

        List<AtomicClaim> claims = decomposer.decompose(answer);
        assertEquals(2, claims.size());
        assertEquals("p2", claims.get(1).citedChunkId());
    }

    @Test
    void sentenceWithoutCitationIsSkipped() {
        String answer = "This sentence has no citation. " +
            "This one does. [ID: p1]";

        List<AtomicClaim> claims = decomposer.decompose(answer);
        assertEquals(1, claims.size());
        assertEquals("p1", claims.get(0).citedChunkId());
    }

    @Test
    void emptyAnswerReturnsEmptyList() {
        assertTrue(decomposer.decompose("").isEmpty());
    }

    @Test
    void answerWithOnlySpacesReturnsEmpty() {
        assertTrue(decomposer.decompose("   ").isEmpty());
    }

    @Test
    void citationStaysWithPrecedingSentence() {
        String answer = "First sentence. [ID: p1] Second sentence. [ID: p2]";
        List<AtomicClaim> claims = decomposer.decompose(answer);

        assertEquals(2, claims.size());
        assertEquals("p1", claims.get(0).citedChunkId());
        assertEquals("First sentence.", claims.get(0).statement());
        assertEquals("p2", claims.get(1).citedChunkId());
        assertEquals("Second sentence.", claims.get(1).statement());
    }

    @Test
    void handlesQuestionMarksAndExclamations() {
        String answer = "Was he American? [ID: p1] Absolutely! [ID: p2]";
        List<AtomicClaim> claims = decomposer.decompose(answer);

        assertEquals(2, claims.size());
        assertEquals("p1", claims.get(0).citedChunkId());
        assertEquals("p2", claims.get(1).citedChunkId());
    }

    @Test
    void handlesCitationAtStart() {
        String answer = "[ID: p1] The moon is rocky.";
        List<AtomicClaim> claims = decomposer.decompose(answer);

        assertEquals(1, claims.size());
        assertEquals("p1", claims.get(0).citedChunkId());
        assertEquals("The moon is rocky.", claims.get(0).statement());
    }

    @Test
    void multipleCitationsInOneSentenceUseFirstChunkId() {
        String answer = "Both were American directors. [ID: p1][ID: p2]";
        List<AtomicClaim> claims = decomposer.decompose(answer);

        assertEquals(1, claims.size());
        assertEquals("p1", claims.get(0).citedChunkId());
    }
}