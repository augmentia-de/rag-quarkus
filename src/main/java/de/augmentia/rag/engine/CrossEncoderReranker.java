package de.augmentia.rag.engine;

import de.augmentia.rag.ai.EmbeddingModelClient;
import de.augmentia.rag.domain.Chunk;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@ApplicationScoped
public class CrossEncoderReranker {

    private static final Logger log = Logger.getLogger(CrossEncoderReranker.class);

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    @Inject
    EmbeddingModelClient embeddingClient;

    public List<Chunk> rerank(String query, List<Chunk> candidates, int topN) {
        if (candidates.isEmpty()) return List.of();
        long t0 = System.nanoTime();

        float[] queryVec = embeddingClient.embed(query);

        var futures = candidates.stream()
            .map(chunk -> CompletableFuture.supplyAsync(
                () -> new ScoredChunk(chunk, cosineSimilarity(queryVec, embeddingClient.embed(chunk.contextualText()))),
                executor))
            .toList();

        var ranked = futures.stream()
            .map(CompletableFuture::join)
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(topN)
            .toList();

        log.debugv("reranker: {0} candidates → top {1} in {2}ms (topScore={3})",
            candidates.size(), ranked.size(), (System.nanoTime() - t0) / 1_000_000,
            ranked.isEmpty() ? "n/a" : String.format("%.4f", ranked.get(0).score));

        return ranked.stream().map(sc -> sc.chunk).toList();
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredChunk(Chunk chunk, double score) {}
}