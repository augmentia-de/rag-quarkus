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
import de.augmentia.rag.repository.GraphNodeRepository;
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
            log.debugv("graphSearch: embedding search for '{0}' → {1} matches", entity, matches.size());
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

        log.debugv("graphSearch: starting BFS from {0} seed nodes, hops={1}, maxNodes={2}",
            visitedNodeIds.size(), hops, maxNodes);

        Queue<String> queue = new LinkedList<>(visitedNodeIds);
        Set<String> alreadyQueued = new HashSet<>(visitedNodeIds);
        Map<String, GraphEdge> relevantEdges = new LinkedHashMap<>();

        int currentHop = 0;
        while (!queue.isEmpty() && currentHop < hops) {
            int levelSize = queue.size();
            log.debugv("graphSearch: BFS hop={0} frontier={1} visited={2}",
                currentHop, levelSize, visitedNodeIds.size());
            for (int i = 0; i < levelSize && visitedNodeIds.size() < maxNodes; i++) {
                String currentId = queue.poll();
                List<GraphEdgeEntity> edges = graphEdgeRepo.findBySourceOrTarget(currentId);
                log.debugv("graphSearch: node='{0}' has {1} edges",
                    currentId, edges.size());
                for (GraphEdgeEntity edgeEntity : edges) {
                    relevantEdges.putIfAbsent(edgeEntity.id, edgeEntity.toDomain());
                    String neighborId = edgeEntity.sourceNodeId.equals(currentId)
                        ? edgeEntity.targetNodeId : edgeEntity.sourceNodeId;
                    if (!alreadyQueued.contains(neighborId) && visitedNodeIds.size() < maxNodes) {
                        alreadyQueued.add(neighborId);
                        visitedNodeIds.add(neighborId);
                        queue.offer(neighborId);
                    }
                }
            }
            currentHop++;
        }

        log.debugv("graphSearch: BFS done — found {0} nodes, {1} edges",
            visitedNodeIds.size(), relevantEdges.size());

        Set<String> missingIds = new LinkedHashSet<>(visitedNodeIds);
        missingIds.removeAll(nodeById.keySet());
        if (!missingIds.isEmpty()) {
            log.debugv("graphSearch: loading {0} BFS-discovered nodes from DB", missingIds.size());
            var bfsNodes = graphNodeRepo.find("id IN ?1", List.copyOf(missingIds)).list();
            for (var entity : bfsNodes) {
                nodeById.putIfAbsent(entity.id, new GraphNode(
                    entity.id, entity.chunkId, entity.entityName,
                    entity.entityType, entity.description, null, entity.createdAt
                ));
            }
        }

        Set<String> chunkIds = visitedNodeIds.stream()
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

        return new GraphSearchResult(
            new ArrayList<>(nodeById.values()),
            new ArrayList<>(relevantEdges.values()),
            chunks
        );
    }

    private List<String> extractEntities(String question) {
        return List.of(question.trim());
    }
}
