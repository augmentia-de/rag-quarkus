package de.augmentia.rag.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class GraphTraversalRepository {

    @PersistenceContext
    EntityManager em;

    @SuppressWarnings("unchecked")
    public List<String> findConnectedNodeIds(List<String> seedNodeIds, int maxHops, int maxNodes) {
        String sql = """
            WITH RECURSIVE bfs_tree AS (
                SELECT id AS node_id, 0 AS depth
                FROM graph_nodes
                WHERE id IN (:seeds)

                UNION ALL

                SELECT child.node_id, b.depth + 1
                FROM bfs_tree b
                JOIN (
                    SELECT source_node_id AS parent, target_node_id AS node_id FROM graph_edges
                    UNION ALL
                    SELECT target_node_id AS parent, source_node_id AS node_id FROM graph_edges
                ) child ON b.node_id = child.parent
                WHERE b.depth < :maxHops
            )
            SELECT node_id
            FROM bfs_tree
            GROUP BY node_id
            ORDER BY MIN(depth) ASC
            LIMIT :maxNodes
            """;

        return em.createNativeQuery(sql)
                 .setParameter("seeds", seedNodeIds)
                 .setParameter("maxHops", maxHops)
                 .setParameter("maxNodes", maxNodes)
                 .getResultList();
    }
}
