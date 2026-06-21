package de.augmentia.rag.engine;

import de.augmentia.rag.ai.EmbeddingModelClient;
import de.augmentia.rag.domain.Chunk;
import de.augmentia.rag.domain.GraphEdge;
import de.augmentia.rag.domain.GraphNode;
import de.augmentia.rag.domain.GraphSearchResult;
import de.augmentia.rag.repository.ChunkEntity;
import de.augmentia.rag.repository.ChunkRepository;
import de.augmentia.rag.repository.GraphEdgeEntity;
import de.augmentia.rag.repository.GraphEdgeRepository;
import de.augmentia.rag.repository.GraphNodeEntity;
import de.augmentia.rag.repository.GraphNodeRepository;
import de.augmentia.rag.repository.GraphTraversalRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class GraphSearchService {

    private static final Logger log = Logger.getLogger(GraphSearchService.class);

    @Inject GraphNodeRepository graphNodeRepo;
    @Inject GraphEdgeRepository graphEdgeRepo;
    @Inject GraphTraversalRepository traversalRepository;
    @Inject ChunkRepository chunkRepo;
    @Inject EmbeddingModelClient embeddingClient;

    public GraphSearchResult search(String question, int hops, int maxNodes) {
        long start = System.nanoTime();

        List<String> queryEntities = extractEntities(question);
        log.debugv("graphSearch: query='{0}' hops={1} maxNodes={2} entities={3}",
            question, hops, maxNodes, queryEntities);

        Set<String> visitedNodeIds = new LinkedHashSet<>();
        Map<String, GraphNode> nodeById = new LinkedHashMap<>();

        for (String entity : queryEntities) {
            long tEmb = System.nanoTime();
            float[] entityVec = embeddingClient.embed(entity);
            log.debugv("graphSearch: embedded entity='{0}' in {1}ms dim={2}",
                entity, (System.nanoTime() - tEmb) / 1_000_000, entityVec.length);

            List<Object[]> matches = graphNodeRepo.searchByEmbedding(entityVec, 5);
            log.debugv("graphSearch: embedding search for '{0}' -> {1} matches", entity, matches.size());
            for (Object[] row : matches) {
                String nodeId = (String) row[0];
                if (visitedNodeIds.add(nodeId)) {
                    nodeById.put(nodeId, new GraphNode(
                        nodeId, (String) row[4], (String) row[1],
                        (String) row[2], (String) row[3], null, null
                    ));
                }
            }
            if (visitedNodeIds.size() >= maxNodes) break;
        }

        if (visitedNodeIds.isEmpty()) {
            log.debugv("graphSearch: no seed nodes found for query='{0}'", question);
            return new GraphSearchResult(List.of(), List.of(), List.of());
        }

        log.debugv("graphSearch: starting CTE traversal from {0} seed nodes, hops={1}, maxNodes={2}",
            visitedNodeIds.size(), hops, maxNodes);

        List<String> connectedIds = traversalRepository.findConnectedNodeIds(
            new ArrayList<>(visitedNodeIds), hops, maxNodes);

        log.debugv("graphSearch: CTE returned {0} connected node ids", connectedIds.size());

        connectedIds.forEach(id -> nodeById.computeIfAbsent(id, k -> {
            GraphNodeEntity entity = graphNodeRepo.find("id", k).firstResult();
            if (entity != null) {
                return new GraphNode(
                    entity.id, entity.chunkId, entity.entityName,
                    entity.entityType, entity.description, null, entity.createdAt
                );
            }
            return null;
        }));

        List<GraphEdge> relevantEdges = graphEdgeRepo.findEdgesBetweenNodes(connectedIds).stream()
            .map(GraphEdgeEntity::toDomain)
            .toList();

        Set<String> chunkIds = connectedIds.stream()
            .map(nodeById::get)
            .filter(Objects::nonNull)
            .map(GraphNode::chunkId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        List<Chunk> chunks = chunkIds.stream()
            .map(chunkRepo::findById)
            .flatMap(opt -> opt.map(ChunkEntity::toDomain).stream())
            .toList();

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        log.infov("Graph search completed in {0}ms: {1} nodes, {2} edges, {3} chunks",
            elapsed, visitedNodeIds.size(), relevantEdges.size(), chunks.size());

        List<GraphNode> finalNodes = connectedIds.stream()
            .map(nodeById::get)
            .filter(Objects::nonNull)
            .toList();

        return new GraphSearchResult(
            finalNodes,
            relevantEdges,
            chunks
        );
    }

    private List<String> extractEntities(String question) {
        return List.of(question.trim());
    }
}
