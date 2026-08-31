package de.augmentia.rag.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Fused LLM service for ingestion: generates a chunk context prefix AND extracts
 * knowledge graph triples in a single call.
 *
 * <p>Replaces two separate calls (contextualization + triple extraction) per chunk.
 * Returns a single JSON object: {"contextPrefix": "...", "triples": [...]}. The raw
 * String is parsed with Jackson (see {@code FusedChunkProcessor}), mirroring the
 * {@link GraphExtractor} pattern.
 */
@Singleton
@RegisterAiService
public interface FusedChunkAnalysisService {

    @SystemMessage("You are a document analysis assistant. Return ONLY valid JSON, no markdown, no explanation.")
    @UserMessage("""
        Analyze the given chunk within its full document context.
        1. Provide a 1-2 sentence context prefix (<=25 words) that situates this chunk in the document.
        2. Extract entity-relation triples as a JSON array:
           [{"src":"...","rel":"...","tgt":"...","desc":"..."}]

        Rules:
        - Use concise entity names (no pronouns, no articles)
        - Relation types: WORKS_AT, LOCATED_IN, PART_OF, CREATED, OWNS, PARENT_OF,
                         EVENT_PARTICIPANT, RELATED_TO, INSTANCE_OF, HAS_PROPERTY, MEMBER_OF
        - Max 5 triples per chunk
        - Only extract well-supported facts

        Return ONLY the JSON object, e.g.:
        {"contextPrefix": "This chunk describes ...", "triples": [{"src":"...","rel":"...","tgt":"...","desc":"..."}]}

        Document title: {title}
        <document>{docText}</document>
        <chunk>{chunkText}</chunk>
        """)
    @Timeout(180000)
    @Retry(maxRetries = 1, delay = 500)
    @CircuitBreaker(requestVolumeThreshold = 20, failureRatio = 0.5, delay = 5000)
    String analyze(@V("title") String title, @V("docText") String docText, @V("chunkText") String chunkText);
}