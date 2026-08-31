package de.augmentia.rag.repository;

import de.augmentia.rag.util.VectorUtils;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Default pgvector-backed vector search over {@code rag_chunks.embedding}.
 *
 * <p>Active whenever the {@code qdrant} build profile is not enabled. Uses the
 * {@code <=>} cosine distance operator on the ivfflat/HNSW index.
 */
@DefaultBean
@ApplicationScoped
public class PgVectorSearchRepository implements VectorSearchRepository {

    private static final Logger log = Logger.getLogger(PgVectorSearchRepository.class);

    @PersistenceContext
    EntityManager em;

    private static final String VECTOR_SEARCH_SQL =
        "SELECT id, 1 - (embedding <=> CAST(:query AS vector)) AS score " +
        "FROM rag_chunks WHERE embedding IS NOT NULL " +
        "ORDER BY embedding <=> CAST(:query AS vector) " +
        "LIMIT :k";

    @Override
    public List<SearchResult> search(float[] queryVector, int k) {
        long t0 = System.nanoTime();
        Query q = em.createNativeQuery(VECTOR_SEARCH_SQL);
        q.setParameter("query", VectorUtils.vectorToString(queryVector));
        q.setParameter("k", k);
        List<?> rows = q.getResultList();
        List<SearchResult> results = rows.stream().map(row -> {
            Object[] cols = (Object[]) row;
            return new SearchResult((String) cols[0], ((Number) cols[1]).floatValue());
        }).toList();
        log.debugv("vectorSearch: k={0} -> {1} results in {2}ms (topScore={3})",
            k, results.size(), (System.nanoTime() - t0) / 1_000_000,
            results.isEmpty() ? "n/a" : String.format("%.4f", results.get(0).getScore()));
        return results;
    }
}