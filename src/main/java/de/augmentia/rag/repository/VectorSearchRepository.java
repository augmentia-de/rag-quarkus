package de.augmentia.rag.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.jboss.logging.Logger;
import java.util.List;

@ApplicationScoped
public class VectorSearchRepository {

    private static final Logger log = Logger.getLogger(VectorSearchRepository.class);

    @PersistenceContext
    EntityManager em;

    private static final String VECTOR_SEARCH_SQL =
        "SELECT id, 1 - (embedding <=> CAST(:query AS vector)) AS score " +
        "FROM rag_chunks WHERE embedding IS NOT NULL " +
        "ORDER BY embedding <=> CAST(:query AS vector) " +
        "LIMIT :k";

    public List<SearchResult> search(float[] queryVector, int k) {
        long t0 = System.nanoTime();
        Query q = em.createNativeQuery(VECTOR_SEARCH_SQL);
        q.setParameter("query", vectorToString(queryVector));
        q.setParameter("k", k);
        List<?> rows = q.getResultList();
        List<SearchResult> results = rows.stream().map(row -> {
            Object[] cols = (Object[]) row;
            return new SearchResult((String) cols[0], ((Number) cols[1]).floatValue());
        }).toList();
        log.debugv("vectorSearch: k={0} → {1} results in {2}ms (topScore={3})",
            k, results.size(), (System.nanoTime() - t0) / 1_000_000,
            results.isEmpty() ? "n/a" : String.format("%.4f", results.get(0).getScore()));
        return results;
    }

    private String vectorToString(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}