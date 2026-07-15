package de.augmentia.rag.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.inject.Singleton;

/**
 * LLM service that extracts knowledge graph triples from text chunks.
 *
 * <p>Returns JSON array of triples: [{"src":"...","rel":"...","tgt":"...","desc":"..."}].
 * Allowed relation types: WORKS_AT, LOCATED_IN, PART_OF, CREATED, OWNS, PARENT_OF,
 * EVENT_PARTICIPANT, RELATED_TO, INSTANCE_OF, HAS_PROPERTY, MEMBER_OF.
 */
@Singleton
@RegisterAiService
public interface GraphExtractor {

    @SystemMessage("You are a knowledge graph extractor. Return ONLY valid JSON.")
    @UserMessage("""
        Extract entities and relations from the following text.
        Return a JSON array of triples: [{"src":"...","rel":"...","tgt":"...","desc":"..."}]

        Rules:
        - Use concise entity names (no pronouns, no articles)
        - Relation types: WORKS_AT, LOCATED_IN, PART_OF, CREATED, OWNS, PARENT_OF,
                         EVENT_PARTICIPANT, RELATED_TO, INSTANCE_OF, HAS_PROPERTY, MEMBER_OF
        - Max 5 triples per text
        - Only extract well-supported facts
        - JSON only, no markdown, no explanation

        Text:
        {text}
        """)
    String extract(@V("text") String text);
}
