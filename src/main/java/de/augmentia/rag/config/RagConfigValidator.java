package de.augmentia.rag.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RagConfigValidator {

    private static final Logger log = Logger.getLogger(RagConfigValidator.class);

    @Inject
    RagConfig config;

    void validate(@Observes StartupEvent event) {
        log.info("Validating RAG configuration...");

        requireNonBlank(config.llm().endpoint(), "rag.llm.endpoint");
        requireNonBlank(config.llm().model(), "rag.llm.model");

        requireNonBlank(config.embedding().endpoint(), "rag.embedding.endpoint");
        requireNonBlank(config.embedding().model(), "rag.embedding.model");

        if (config.retrieve().topK() < 1 || config.retrieve().topK() > 10000) {
            throw new IllegalStateException("rag.retrieve.top-k must be between 1 and 10000");
        }
        if (config.rerank().topN() < 1 || config.rerank().topN() > config.retrieve().topK()) {
            throw new IllegalStateException("rag.rerank.top-n must be between 1 and rag.retrieve.top-k");
        }
        if (config.judge().tauClaim() < 0 || config.judge().tauClaim() > 1) {
            throw new IllegalStateException("rag.judge.tau-claim must be between 0.0 and 1.0");
        }
        if (config.crag().maxHops() < 0 || config.crag().maxHops() > 10) {
            throw new IllegalStateException("rag.crag.max-hops must be between 0 and 10");
        }

        log.info("RAG configuration validated successfully");
    }

    private void requireNonBlank(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required config property '%s' is not set".formatted(key));
        }
    }
}