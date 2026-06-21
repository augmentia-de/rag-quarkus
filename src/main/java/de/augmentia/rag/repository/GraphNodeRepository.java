package de.augmentia.rag.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.jboss.logging.Logger;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GraphNodeRepository implements PanacheRepository<GraphNodeEntity> {

    private static final Logger log = Logger.getLogger(GraphNodeRepository.class);

    @PersistenceContext
    EntityManager em;

    public Optional<GraphNodeEntity> findByEntityName(String entityName) {
        log.debugv("graphNodeRepo: findByEntityName('{0}')", entityName);
        var result = find("LOWER(entityName) = LOWER(?1)", entityName).firstResultOptional();
        log.debugv("graphNodeRepo: findByEntityName('{0}') → found={1}", entityName, result.isPresent());
        return result;
    }

    public List<GraphNodeEntity> findByEntityNameIn(List<String> names) {
        log.debugv("graphNodeRepo: findByEntityNameIn({0})", names);
        var result = find("LOWER(entityName) IN (?1)", names.stream().map(String::toLowerCase).toList()).list();
        log.debugv("graphNodeRepo: findByEntityNameIn({0}) → {1} results", names, result.size());
        return result;
    }

    public List<GraphNodeEntity> findByChunkId(String chunkId) {
        log.debugv("graphNodeRepo: findByChunkId('{0}')", chunkId);
        var result = find("chunkId", chunkId).list();
        log.debugv("graphNodeRepo: findByChunkId('{0}') → {1} results", chunkId, result.size());
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> searchByEmbedding(float[] queryVec, int limit) {
        long t0 = System.nanoTime();
        String vecStr = GraphNodeEntity.vectorToString(queryVec);
        var result = em.createNativeQuery(
            "SELECT id, entity_name, entity_type, description, chunk_id, " +
            "1 - (embedding <=> CAST(:vec AS vector)) AS score " +
            "FROM graph_nodes WHERE embedding IS NOT NULL " +
            "ORDER BY embedding <=> CAST(:vec AS vector) LIMIT :limit")
            .setParameter("vec", vecStr)
            .setParameter("limit", limit)
            .getResultList();
        log.debugv("graphNodeRepo: searchByEmbedding(limit={0}) → {1} results in {2}ms",
            limit, result.size(), (System.nanoTime() - t0) / 1_000_000);
        return result;
    }
}
