package de.augmentia.rag.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class EmbeddingModelClient {

    private static final Logger log = Logger.getLogger(EmbeddingModelClient.class);

    EmbeddingModel embeddingModel;

    @ConfigProperty(name = "rag.embedding.endpoint")
    String endpoint;
    @ConfigProperty(name = "rag.embedding.api-key")
    String apiKey;
    @ConfigProperty(name = "rag.embedding.model")
    String model;

    @PostConstruct
    void init() {
        log.debugv("embedding: initializing with endpoint='{0}' model='{1}'", endpoint, model);
        embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .modelName(model)
                .timeout(Duration.ofSeconds(300))
                .build();
    }

    @Timeout(10000)
    @Retry(maxRetries = 2, delay = 500)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000)
    public float[] embed(String text) {
        long t0 = System.nanoTime();
        Embedding embedding = embeddingModel.embed(text).content();
        float[] vec = embedding.vector();
        log.debugv("embedding: embed(text={0}… dim={1}) in {2}ms",
            text.substring(0, Math.min(40, text.length())), vec.length,
            (System.nanoTime() - t0) / 1_000_000);
        return vec;
    }

    public List<float[]> embedBatch(List<String> texts) {
        long t0 = System.nanoTime();
        var segments = texts.stream().map(TextSegment::from).toList();
        var embeddings = embeddingModel.embedAll(segments).content();
        var result = embeddings.stream()
            .map(Embedding::vector)
            .toList();
        log.debugv("embedding: batch({0} texts) in {1}ms, dim={2}",
            texts.size(), (System.nanoTime() - t0) / 1_000_000,
            result.isEmpty() ? 0 : result.get(0).length);
        return result;
    }
}