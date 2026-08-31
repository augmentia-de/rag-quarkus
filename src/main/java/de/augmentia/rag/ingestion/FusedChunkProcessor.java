package de.augmentia.rag.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.rag.ai.FusedChunkAnalysisService;
import de.augmentia.rag.ai.LlmLogger;
import de.augmentia.rag.domain.GraphTriple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Bundles chunk contextualization and graph triple extraction into a single LLM call.
 *
 * <p>Replaces the separate {@code ContextualizerAiService} and {@code GraphExtractor}
 * calls for the main ingestion path (when graph extraction is enabled). The returned
 * {@link AnalysisResult} carries the enriched contextual text plus extracted triples.
 * On failure the caller falls back to the untouched chunk with no triples, mirroring
 * the existing resilience of {@link IngestionPipeline}.
 */
@ApplicationScoped
public class FusedChunkProcessor {

    private static final Logger log = Logger.getLogger(FusedChunkProcessor.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * @param contextualText enriched chunk text (context prefix + original text)
     * @param triples        extracted knowledge graph triples
     * @param ok             false when the fused analysis failed (no enrichment, no triples)
     */
    public record AnalysisResult(String contextualText, List<GraphTriple> triples, boolean ok) {}

    @Inject
    FusedChunkAnalysisService fusedService;

    @Inject
    LlmLogger llmLogger;

    public AnalysisResult process(String title, String docText, String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            return new AnalysisResult(chunkText, List.of(), false);
        }
        String safeDocText = docText == null ? chunkText : docText;
        try {
            String prompt = "Document title: '" + title + "'\n<document>\n" + safeDocText
                + "\n</document>\n\nChunk:\n<chunk>\n" + chunkText + "\n</chunk>";
            llmLogger.logRequest("FusedAnalyzer", "analyze", prompt);
            String raw = llmLogger.logAndExecute("FusedAnalyzer",
                () -> fusedService.analyze(title, safeDocText, chunkText));
            return parse(raw, chunkText);
        } catch (Exception e) {
            log.warnv("fusedProcessor: fused analysis FAILED for chunk: {0}", e.getMessage());
            log.debugv(e, "fusedProcessor: full exception");
            return new AnalysisResult(chunkText, List.of(), false);
        }
    }

    private AnalysisResult parse(String raw, String chunkText) {
        try {
            var root = JSON.readTree(raw);
            String prefix = root.path("contextPrefix").asText(null);
            if (prefix == null || prefix.isBlank()) {
                prefix = root.path("context_prefix").asText(null);
            }
            if (prefix == null || prefix.isBlank()) {
                log.warnv("fusedProcessor: missing contextPrefix in response, falling back for chunk");
                return new AnalysisResult(chunkText, List.of(), false);
            }
            List<GraphTriple> triples = List.of();
            if (root.has("triples") && root.get("triples").isArray()) {
                triples = JSON.convertValue(root.get("triples"),
                    JSON.getTypeFactory().constructCollectionType(List.class, GraphTriple.class));
            }
            String contextualText = (prefix + "\n" + chunkText).trim();
            log.debugv("fusedProcessor: contextPrefix='{0}' triples={1}", prefix, triples.size());
            return new AnalysisResult(contextualText, triples, true);
        } catch (Exception e) {
            log.warnv("fusedProcessor: JSON parse FAILED, falling back for chunk: {0}", e.getMessage());
            return new AnalysisResult(chunkText, List.of(), false);
        }
    }
}