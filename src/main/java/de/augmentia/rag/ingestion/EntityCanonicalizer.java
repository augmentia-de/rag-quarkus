package de.augmentia.rag.ingestion;

import de.augmentia.rag.config.RagConfig;
import de.augmentia.rag.repository.GraphNodeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Canonicalizes entity names against already-persisted graph nodes.
 *
 * <p>Prevents cross-batch fragmentation of the knowledge graph: the same real-world
 * entity (e.g. "Quarkus" vs "Quarkus Framework" vs "Red Hat Quarkus") is resolved to
 * a single node via trigram similarity against {@code graph_nodes.entity_name}.
 */
@ApplicationScoped
public class EntityCanonicalizer {

    private static final Logger log = Logger.getLogger(EntityCanonicalizer.class);

    @Inject
    GraphNodeRepository graphNodeRepo;

    @Inject
    RagConfig config;

    public String canonicalize(String entityName) {
        String normalized = entityName.trim().toLowerCase();
        Optional<String> existing = graphNodeRepo.findSimilarEntityName(
            normalized, config.graph().entityResolution().similarityThreshold());
        if (existing.isPresent() && !existing.get().equals(normalized)) {
            log.debugv("entityCanonicalizer: '{0}' -> '{1}'", normalized, existing.get());
            return existing.get();
        }
        if (existing.isEmpty()) {
            log.debugv("entityCanonicalizer: '{0}' -> new node", normalized);
        }
        return normalized;
    }
}