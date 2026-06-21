package de.augmentia.rag.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VerificationResultTest {

    @Test
    void faithfulWhenNoFailures() {
        var result = new VerificationResult(true, List.of(), List.of(0.9, 0.8));
        assertTrue(result.isFaithful());
        assertTrue(result.unverifiedClaims().isEmpty());
        assertEquals(2, result.claimScores().size());
    }

    @Test
    void unfaithfulWhenFailuresExist() {
        var result = new VerificationResult(false, List.of("Claim A"), List.of(0.1));
        assertFalse(result.isFaithful());
        assertEquals(1, result.unverifiedClaims().size());
        assertEquals("Claim A", result.unverifiedClaims().getFirst());
    }
}