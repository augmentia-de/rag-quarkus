package de.augmentia.rag.ingestion;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.text.Normalizer;

@ApplicationScoped
public class CorpusCleaner {

    private static final Logger log = Logger.getLogger(CorpusCleaner.class);

    public String normalize(String text) {
        int lenBefore = text.length();
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC);
        s = s.replace("\u00AD", "");
        s = s.replaceAll("[ \\t]+", " ");
        s = s.strip();
        log.debugv("cleaner: normalized text from {0} to {1} chars", lenBefore, s.length());
        return s;
    }
}