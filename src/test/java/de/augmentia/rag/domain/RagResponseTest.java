package de.augmentia.rag.domain;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RagResponseTest {

    @Test
    void abstainReturnsCorrectResponse() {
        RagResponse response = RagResponse.abstain();

        assertTrue(response.abstained());
        assertEquals("I do not have enough verifiable evidence to answer this question.", response.answer());
        assertTrue(response.citations().isEmpty());
        assertTrue(response.verification().isEmpty());
    }

    @Test
    void fullResponseContainsAllFields() {
        var verification = List.of(new VerificationResult(true, List.of(), List.of()));
        var response = new RagResponse("Scott Derrickson is an American director. [ID: p1]", false,
            List.of("p1"), verification);

        assertFalse(response.abstained());
        assertEquals(1, response.citations().size());
        assertEquals("p1", response.citations().getFirst());
        assertEquals(1, response.verification().size());
    }

    @Test
    void equalityWorks() {
        var v1 = new VerificationResult(true, List.of(), List.of());
        var r1 = new RagResponse("answer", false, List.of("p1"), List.of(v1));
        var r2 = new RagResponse("answer", false, List.of("p1"), List.of(v1));

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }
}