package de.augmentia.rag.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

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

    interface Graph {
        @WithDefault("2") int hops();
        @WithDefault("20") int maxNodes();
        @WithDefault("false") boolean enabled();
        Extraction extraction();

        interface Extraction {
            @WithDefault("5") int maxTriplesPerChunk();
            @WithDefault("0.0") double temperature();
        }
    }

    interface Auth {
        @WithDefault("dev-key-change-me") String apiKey();
    }

    interface Llm {
        String endpoint();
        String model();
        @WithDefault("none") String apiKey();
    }

    interface Embedding {
        String endpoint();
        String model();
        @WithDefault("none") String apiKey();
    }

    interface Retrieve {
        @WithDefault("150") int topK();
        @WithDefault("60") int rrfK();
    }

    interface Rerank {
        @WithDefault("20") int topN();
    }

    interface Chunk {
        @WithDefault("256") int targetTokens();
        @WithDefault("32") int overlap();
    }

    interface Judge {
        @WithDefault("0.01") double tauClaim();
        @WithDefault("0.01") double tauAbstain();
    }

    interface Crag {
        @WithDefault("3") int maxHops();
        @WithDefault("0.1") double thresholdOk();
        @WithDefault("0.4") double thresholdBad();
    }
}