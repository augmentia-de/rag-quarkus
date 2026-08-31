package de.augmentia.rag.repository;

import java.util.List;

/**
 * Abstraction over the vector similarity store used for retrieval.
 *
 * <p>Default implementation is pgvector ({@link PgVectorSearchRepository}). Under high
 * load the backing store can be swapped for a dedicated vector database
 * ({@link QdrantVectorSearchRepository}, active via the {@code qdrant} build profile)
 * without touching the retrieval engine.
 */
public interface VectorSearchRepository {

    /**
     * Top-k cosine-similarity search. Scores are in [0,1], higher is more similar.
     *
     * @param queryVector embedding of the query
     * @param k           maximum number of results
     * @return results ordered by descending similarity
     */
    List<SearchResult> search(float[] queryVector, int k);
}