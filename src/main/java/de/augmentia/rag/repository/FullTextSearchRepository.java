package de.augmentia.rag.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.jboss.logging.Logger;
import java.util.List;

@ApplicationScoped
public class FullTextSearchRepository {

    private static final Logger log = Logger.getLogger(FullTextSearchRepository.class);

    @PersistenceContext
    EntityManager em;

    private static final String BM25_SEARCH_SQL =
        "SELECT id, ts_rank(tsv, plainto_tsquery('english', :query)) AS score " +
        "FROM rag_chunks " +
        "WHERE tsv @@ plainto_tsquery('english', :query) " +
        "ORDER BY score DESC " +
        "LIMIT :k";

    public List<SearchResult> search(String queryText, int k) {
        long t0 = System.nanoTime();
        Query q = em.createNativeQuery(BM25_SEARCH_SQL);
        q.setParameter("query", queryText);
        q.setParameter("k", k);
        List<?> rows = q.getResultList();
        List<SearchResult> results = rows.stream().map(row -> {
            Object[] cols = (Object[]) row;
            return new SearchResult((String) cols[0], ((Number) cols[1]).floatValue());
        }).toList();
        log.debugv("fullTextSearch: query='{0}' k={1} → {2} results in {3}ms (topScore={4})",
            queryText, k, results.size(), (System.nanoTime() - t0) / 1_000_000,
            results.isEmpty() ? "n/a" : String.format("%.4f", results.get(0).getScore()));
        return results;
    }
}