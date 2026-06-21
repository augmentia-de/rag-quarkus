package de.augmentia.rag.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import java.util.List;

@ApplicationScoped
public class GraphEdgeRepository implements PanacheRepository<GraphEdgeEntity> {

    private static final Logger log = Logger.getLogger(GraphEdgeRepository.class);

    public List<GraphEdgeEntity> findBySourceNodeId(String sourceNodeId) {
        log.debugv("graphEdgeRepo: findBySourceNodeId('{0}')", sourceNodeId);
        var result = find("sourceNodeId", sourceNodeId).list();
        log.debugv("graphEdgeRepo: findBySourceNodeId → {0} edges", result.size());
        return result;
    }

    public List<GraphEdgeEntity> findByTargetNodeId(String targetNodeId) {
        log.debugv("graphEdgeRepo: findByTargetNodeId('{0}')", targetNodeId);
        var result = find("targetNodeId", targetNodeId).list();
        log.debugv("graphEdgeRepo: findByTargetNodeId → {0} edges", result.size());
        return result;
    }

    public List<GraphEdgeEntity> findBySourceOrTarget(String nodeId) {
        log.debugv("graphEdgeRepo: findBySourceOrTarget('{0}')", nodeId);
        var result = find("sourceNodeId = ?1 OR targetNodeId = ?1", nodeId).list();
        log.debugv("graphEdgeRepo: findBySourceOrTarget → {0} edges", result.size());
        return result;
    }

    public List<GraphEdgeEntity> findByRelationType(String relationType) {
        log.debugv("graphEdgeRepo: findByRelationType('{0}')", relationType);
        var result = find("relationType", relationType).list();
        log.debugv("graphEdgeRepo: findByRelationType → {0} edges", result.size());
        return result;
    }

    public List<GraphEdgeEntity> findEdgesBetweenNodes(List<String> nodeIds) {
        log.debugv("graphEdgeRepo: findEdgesBetweenNodes({0})", nodeIds);
        var result = find("sourceNodeId IN ?1 AND targetNodeId IN ?1", nodeIds).list();
        log.debugv("graphEdgeRepo: findEdgesBetweenNodes → {0} edges", result.size());
        return result;
    }
}
