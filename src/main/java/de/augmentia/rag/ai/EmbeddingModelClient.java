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

/**
 * Wraps OpenAI-compatible embedding model with fault tolerance.
 *
 * <p>Provides single-text and batch embedding with 10s timeout, 2 retries,
 * and circuit breaker. Used for query embedding, reranking, and graph entity search.
 */
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
        log.infov("embedding: INIT — endpoint='{0}' model='{1}'", endpoint, model);
        embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .modelName(model)
                .timeout(Duration.ofSeconds(300))
                .build();
        log.infov("embedding: INIT done — model ready");
    }

    @Timeout(10000)
    @Retry(maxRetries = 2, delay = 500)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 5000)
    public float[] embed(String text) {
        long t0 = System.nanoTime();
        try {
            Embedding embedding = embeddingModel.embed(text).content();
            float[] vec = embedding.vector();
            log.debugv("embedding: embed OK dim={0} text='{1}' in {2}ms",
                vec.length, text.substring(0, Math.min(60, text.length())),
                (System.nanoTime() - t0) / 1_000_000);
            return vec;
        } catch (Exception e) {
            log.errorv("embedding: embed FAILED in {0}ms: {1}",
                (System.nanoTime() - t0) / 1_000_000, e.getMessage());
            throw e;
        }
    }

    public List<float[]> embedBatch(List<String> texts) {
        long t0 = System.nanoTime();
        try {
            log.debugv("embedding: batch START — {0} texts", texts.size());
            var segments = texts.stream().map(TextSegment::from).toList();
            var embeddings = embeddingModel.embedAll(segments).content();
            var result = embeddings.stream()
                .map(Embedding::vector)
                .toList();
            log.infov("embedding: batch DONE — {0} vectors dim={1} in {2}ms",
                result.size(), result.isEmpty() ? 0 : result.get(0).length,
                (System.nanoTime() - t0) / 1_000_000);
            return result;
        } catch (Exception e) {
            log.errorv("embedding: batch FAILED in {0}ms: {1}",
                (System.nanoTime() - t0) / 1_000_000, e.getMessage());
            throw e;
        }
    }
}