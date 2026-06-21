package de.augmentia.rag.ingestion;

import de.augmentia.rag.domain.Chunk;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class StructureAwareChunker {

    private static final Logger log = Logger.getLogger(StructureAwareChunker.class);
    private static final int TARGET_TOKENS = 256;
    private static final int OVERLAP = 32;

    public List<Chunk> chunk(Chunk source) {
        String[] sentences = splitSentences(source.text());
        if (sentences.length == 0) {
            sentences = new String[]{source.text()};
        }
        log.debugv("chunker: doc='{0}' has {1} sentences", source.id(), sentences.length);

        List<Chunk> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentTokens = 0;

        for (String sentence : sentences) {
            int tokenCount = estimateTokens(sentence);

            if (!current.isEmpty() && currentTokens + tokenCount > TARGET_TOKENS) {
                chunks.add(buildChunk(source, current));

                if (OVERLAP > 0 && !current.isEmpty()) {
                    String lastSentence = current.getLast();
                    current = new ArrayList<>(List.of(lastSentence));
                    currentTokens = estimateTokens(lastSentence);
                } else {
                    current.clear();
                    currentTokens = 0;
                }
            }

            current.add(sentence);
            currentTokens += tokenCount;
        }

        if (!current.isEmpty()) {
            chunks.add(buildChunk(source, current));
        }

        log.debugv("chunker: doc='{0}' → {1} chunks", source.id(), chunks.size());
        return chunks;
    }

    private Chunk buildChunk(Chunk source, List<String> sentences) {
        String text = String.join(" ", sentences);
        return new Chunk(
            UUID.nameUUIDFromBytes((source.id() + ":" + text.hashCode()).getBytes()).toString(),
            source.docId(),
            source.title(),
            text,
            text,
            source.goldForQuestionIds()
        );
    }

    private String[] splitSentences(String text) {
        return text.split("(?<=[.!?])\\s+");
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 2.5);
    }
}