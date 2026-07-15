package de.augmentia.rag.ingestion;

import de.augmentia.rag.domain.Chunk;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class StructureAwareChunker {

    private static final Logger log = Logger.getLogger(StructureAwareChunker.class);
    private static final int DEFAULT_TARGET_TOKENS = 256;
    private static final int DEFAULT_OVERLAP = 32;

    @Inject
    de.augmentia.rag.config.RagConfig config;

    public List<Chunk> chunk(Chunk source) {
        int targetTokens = config != null ? config.chunk().targetTokens() : DEFAULT_TARGET_TOKENS;
        int overlap = config != null ? config.chunk().overlap() : DEFAULT_OVERLAP;
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

            if (!current.isEmpty() && currentTokens + tokenCount > targetTokens) {
                chunks.add(buildChunk(source, current));

                if (overlap > 0 && !current.isEmpty()) {
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