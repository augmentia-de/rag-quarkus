package de.augmentia.rag.ingestion;

import de.augmentia.rag.ai.ContextualizerAiService;
import de.augmentia.rag.domain.Chunk;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;

@ApplicationScoped
public class Contextualizer {

    private static final Logger log = Logger.getLogger(Contextualizer.class);

    @Inject
    ContextualizerAiService contextualizerAi;

    public List<Chunk> contextualize(List<Chunk> chunks, Map<String, String> docLookup) {
        log.debugv("contextualizer: contextualizing {0} chunks", chunks.size());
        List<Chunk> results = new ArrayList<>();
        int successCount = 0;

        for (Chunk chunk : chunks) {
            try {
                String docText = docLookup.getOrDefault(chunk.docId(), chunk.text());
                String prompt = "Document title: '" + chunk.title() + "'\n<document>\n" + docText + "\n</document>\n\nChunk:\n<chunk>\n" + chunk.text() + "\n</chunk>\n\nGive a short single-sentence context (<=25 words) that situates this chunk within the document.";
                String context = contextualizerAi.contextualize(prompt);

                results.add(new Chunk(
                    chunk.id(), chunk.docId(), chunk.title(), chunk.text(),
                    context.isBlank() ? chunk.text() : context + "\n" + chunk.text(),
                    chunk.goldForQuestionIds()
                ));
                if (!context.isBlank()) {
                    successCount++;
                    log.debugv("contextualizer: chunk '{0}' — context='{1}'", chunk.id(), context);
                } else {
                    log.debugv("contextualizer: chunk '{0}' — empty context, using raw text", chunk.id());
                }
            } catch (Exception e) {
                log.warnv("contextualizer: failed for chunk {0}: {1}", chunk.id(), e.getMessage());
                results.add(chunk);
            }
        }

        log.debugv("contextualizer: done — {0}/{1} chunks contextualized successfully",
            successCount, chunks.size());
        return results;
    }
}