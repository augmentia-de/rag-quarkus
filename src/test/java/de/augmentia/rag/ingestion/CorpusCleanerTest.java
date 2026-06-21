package de.augmentia.rag.ingestion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CorpusCleanerTest {

    private final CorpusCleaner cleaner = new CorpusCleaner();

    @Test
    void normalizesLigatureToAscii() {
        assertEquals("the final report was ready", cleaner.normalize("the ﬁnal report\twas ready"));
    }

    @Test
    void collapsesMultipleSpaces() {
        assertEquals("hello world", cleaner.normalize("hello   world"));
    }

    @Test
    void collapsesTabsAndSpaces() {
        assertEquals("a b", cleaner.normalize("a\t\tb"));
    }

    @Test
    void stripsLeadingAndTrailingWhitespace() {
        assertEquals("hello", cleaner.normalize("  hello  "));
    }

    @Test
    void removesSoftHyphens() {
        assertEquals("cafe", cleaner.normalize("caf\u00ADe"));
    }

    @Test
    void emptyStringStaysEmpty() {
        assertEquals("", cleaner.normalize(""));
    }

    @Test
    void handlesNullBytesGracefully() {
        assertNotNull(cleaner.normalize("test\u0000string"));
    }
}