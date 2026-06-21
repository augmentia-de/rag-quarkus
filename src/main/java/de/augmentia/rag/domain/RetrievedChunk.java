package de.augmentia.rag.domain;

public record RetrievedChunk(
    Chunk chunk,
    double denseScore,
    double sparseScore,
    double rrfScore,
    double rerankScore
) {
    public RetrievedChunk withRerankScore(double score) {
        return new RetrievedChunk(chunk, denseScore, sparseScore, rrfScore, score);
    }
}