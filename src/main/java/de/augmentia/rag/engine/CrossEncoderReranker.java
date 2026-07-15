package de.augmentia.rag.engine;

import de.augmentia.rag.ai.EmbeddingModelClient;
import de.augmentia.rag.domain.Chunk;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Bi-encoder reranker using cosine similarity (despite the class name).
 *
 * <p>Embeds query and candidate texts separately, computes pairwise cosine
 * similarity, returns top-N candidates sorted by score.
 */
@ApplicationScoped
public class CrossEncoderReranker {

    private static final Logger log = Logger.getLogger(CrossEncoderReranker.class);

    @Inject
    EmbeddingModelClient embeddingClient;

    /**
     * Reranks candidates by cosine similarity to the query.
     * Returns topN candidates sorted by descending score.
     */
    public List<Chunk> rerank(String query, List<Chunk> candidates, int topN) {
        if (candidates.isEmpty()) return List.of();
        long t0 = System.nanoTime();

        float[] queryVec = embeddingClient.embed(query);
        List<String> candidateTexts = candidates.stream()
            .map(Chunk::contextualText)
            .toList();
        List<float[]> candidateVecs = embeddingClient.embedBatch(candidateTexts);

        List<ScoredChunk> ranked = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            double score = cosineSimilarity(queryVec, candidateVecs.get(i));
            ranked.add(new ScoredChunk(candidates.get(i), score));
        }
        ranked.sort((a, b) -> Double.compare(b.score(), a.score()));

        log.debugv("reranker: {0} candidates -> top {1} in {2}ms (topScore={3})",
            candidates.size(), Math.min(topN, ranked.size()), (System.nanoTime() - t0) / 1_000_000,
            ranked.isEmpty() ? "n/a" : String.format("%.4f", ranked.get(0).score()));

        return ranked.stream().limit(topN).map(ScoredChunk::chunk).toList();
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
