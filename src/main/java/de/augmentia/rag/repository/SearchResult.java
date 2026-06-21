package de.augmentia.rag.repository;

public class SearchResult {
    private String chunkId;
    private float score;

    public SearchResult() {}

    public SearchResult(String chunkId, float score) {
        this.chunkId = chunkId;
        this.score = score;
    }

    public String getChunkId() { return chunkId; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public float getScore() { return score; }
    public void setScore(float score) { this.score = score; }
}