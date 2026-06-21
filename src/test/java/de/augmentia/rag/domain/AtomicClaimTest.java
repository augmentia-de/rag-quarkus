package de.augmentia.rag.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtomicClaimTest {

    @Test
    void createsClaimWithStatementAndCitation() {
        var claim = new AtomicClaim("Scott Derrickson is an American director.", "p1");
        assertEquals("Scott Derrickson is an American director.", claim.statement());
        assertEquals("p1", claim.citedChunkId());
    }

    @Test
    void equalityWorks() {
        var c1 = new AtomicClaim("test", "p1");
        var c2 = new AtomicClaim("test", "p1");
        assertEquals(c1, c2);
    }

    @Test
    void differentStatementsAreNotEqual() {
        var c1 = new AtomicClaim("Statement A.", "p1");
        var c2 = new AtomicClaim("Statement B.", "p1");
        assertNotEquals(c1, c2);
    }
}