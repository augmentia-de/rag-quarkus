package de.augmentia.rag.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Root configuration mapping for the {@code rag.*} namespace.
 * All properties can be overridden via application.properties, env vars, or system properties.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "rag")
public interface RagConfig {

    Llm llm();
    Embedding embedding();
    Retrieve retrieve();
    Rerank rerank();
    Chunk chunk();
    Judge judge();
    Crag crag();
    Auth auth();
    Graph graph();

    /** GraphRAG: traversal hops, max nodes, toggle for graph augmentation. */
    interface Graph {
        @WithDefault("2") int hops();
        @WithDefault("20") int maxNodes();
        @WithDefault("false") boolean enabled();
        Extraction extraction();

        /** LLM-based triple extraction during ingestion. */
        interface Extraction {
            @WithDefault("5") int maxTriplesPerChunk();
            @WithDefault("0.0") double temperature();
        }
    }

    /** API key for request authentication. */
    interface Auth {
        @WithDefault("dev-key-change-me") String apiKey();
    }

    /** LLM endpoint, model, API key, and logging config. */
    interface Llm {
        String endpoint();
        String model();
        @WithDefault("none") String apiKey();
        Logging logging();

        /** Controls LLM request/response logging with truncation. */
        interface Logging {
            @WithDefault("false") boolean enabled();
            @WithDefault("1000") int maxContentLength();
        }
    }

    /** Embedding model endpoint and model name. */
    interface Embedding {
        String endpoint();
        String model();
        @WithDefault("none") String apiKey();
    }

    /**
     * Retrieval parameters: topK (max candidates), rrfK (RRF smoothing),
     * similarityRatio (dense score threshold relative to max).
     */
    interface Retrieve {
        @WithDefault("150") int topK();
        @WithDefault("60") int rrfK();
        @WithDefault("0.6") double similarityRatio();
    }

    /** Reranking: how many chunks survive cross-encoder reranking. */
    interface Rerank {
        @WithDefault("20") int topN();
    }

    /** Ingestion chunking: target token size and overlap. */
    interface Chunk {
        @WithDefault("256") int targetTokens();
        @WithDefault("32") int overlap();
    }

    /** Faithfulness thresholds: tauClaim per claim, tauAbstain overall. */
    interface Judge {
        @WithDefault("0.01") double tauClaim();
        @WithDefault("0.01") double tauAbstain();
    }

    /** Corrective RAG: max hops, confidence thresholds for OK/bad retrieval. */
    interface Crag {
        @WithDefault("3") int maxHops();
        @WithDefault("0.1") double thresholdOk();
        @WithDefault("0.4") double thresholdBad();
    }
}