package de.augmentia.rag.ingestion;

import de.augmentia.rag.ai.EmbeddingModelClient;
import de.augmentia.rag.ai.GraphExtractor;
import de.augmentia.rag.config.RagConfig;
import de.augmentia.rag.domain.Chunk;
import de.augmentia.rag.domain.GraphTriple;
import de.augmentia.rag.repository.ChunkEntity;
import de.augmentia.rag.repository.ChunkRepository;
import de.augmentia.rag.repository.GraphEdgeEntity;
import de.augmentia.rag.repository.GraphEdgeRepository;
import de.augmentia.rag.repository.GraphNodeEntity;
import de.augmentia.rag.repository.GraphNodeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class IngestionPipeline {

    @PersistenceContext
    EntityManager em;

    private static final Logger log = Logger.getLogger(IngestionPipeline.class);

    @Inject CorpusCleaner cleaner;
    @Inject NearDuplicateDeduper deduper;
    @Inject StructureAwareChunker chunker;
    @Inject Contextualizer contextualizer;
    @Inject ChunkRepository chunkRepo;
    @Inject EmbeddingModelClient embeddingClient;
    @Inject RagConfig config;
    @Inject GraphExtractor graphExtractor;
    @Inject GraphNodeRepository graphNodeRepo;
    @Inject GraphEdgeRepository graphEdgeRepo;

    @Transactional
    public IngestionResult ingest(List<Chunk> rawDocuments) {
        long t0 = System.currentTimeMillis();
        int inputCount = rawDocuments.size();
        log.debugv("ingest: starting with {0} documents", inputCount);

        List<Chunk> cleaned = rawDocuments.stream()
            .map(c -> new Chunk(c.id(), c.docId(), c.title(),
                cleaner.normalize(c.text()), cleaner.normalize(c.contextualText()),
                c.goldForQuestionIds()))
            .toList();
        log.debugv("ingest: cleaning done, {0}/{1} documents kept after normalization",
            cleaned.size(), inputCount);

        var dedupResult = deduper.deduplicate(cleaned);
        log.debugv("ingest: dedup done, dropped {0} duplicates, {1} kept",
            dedupResult.droppedCount(), dedupResult.kept().size());

        List<Chunk> chunked = new ArrayList<>();
        for (Chunk doc : dedupResult.kept()) {
            chunked.addAll(chunker.chunk(doc));
        }
        log.debugv("ingest: chunking done, {0} chunks from {1} documents",
            chunked.size(), dedupResult.kept().size());

        Map<String, String> docLookup = dedupResult.kept().stream()
            .collect(Collectors.toMap(Chunk::id, Chunk::text));

        List<Chunk> contextualized = contextualizer.contextualize(chunked, docLookup);
        log.debugv("ingest: contextualization done, {0} chunks contextualized",
            contextualized.size());

        if (config.graph().enabled()) {
            log.debugv("ingest: graph extraction enabled, extracting from {0} chunks",
                contextualized.size());
            extractGraph(contextualized);
        }

        List<String> texts = contextualized.stream().map(Chunk::contextualText).toList();
        log.debugv("ingest: embedding {0} texts, batch size {1}",
            texts.size(), texts.size());
        long t1 = System.currentTimeMillis();
        List<float[]> embeddings = embeddingClient.embedBatch(texts);
        log.debugv("ingest: embedding done in {0}ms, dim={1}",
            System.currentTimeMillis() - t1,
            embeddings.isEmpty() ? 0 : embeddings.get(0).length);

        var entities = new ArrayList<ChunkEntity>();
        for (int i = 0; i < contextualized.size(); i++) {
            var entity = ChunkEntity.fromDomain(contextualized.get(i));
            entities.add(entity);
        }

        chunkRepo.persist(entities);
        log.debugv("ingest: persisted {0} chunk entities", entities.size());

        int updated = 0;
        for (int i = 0; i < entities.size(); i++) {
            String vecStr = vectorToString(embeddings.get(i));
            int rows = em.createNativeQuery(
                "UPDATE rag_chunks SET embedding = CAST(:vec AS vector) WHERE id = :id")
                .setParameter("vec", vecStr)
                .setParameter("id", entities.get(i).id)
                .executeUpdate();
            if (rows > 0) updated++;
        }
        log.debugv("ingest: updated embeddings for {0}/{1} chunks", updated, entities.size());

        long elapsed = System.currentTimeMillis() - t0;
        log.debugv("ingest: complete in {0}ms — input={1} dropped={2} indexed={3} graph={4}",
            elapsed, inputCount, dedupResult.droppedCount(), entities.size(),
            config.graph().enabled());

        return new IngestionResult(inputCount, dedupResult.droppedCount(),
            entities.size());
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

    private static final ObjectMapper JSON = new ObjectMapper();

    private void extractGraph(List<Chunk> chunks) {
        long t0 = System.currentTimeMillis();
        List<GraphTriple> allTriples = new ArrayList<>();
        int failCount = 0;
        for (Chunk chunk : chunks) {
            try {
                String raw = graphExtractor.extract(chunk.contextualText());
                List<GraphTriple> parsed = JSON.readValue(raw,
                    new TypeReference<List<GraphTriple>>() {});
                allTriples.addAll(parsed);
                log.debugv("graph: extracted {0} triples from chunk {1}", parsed.size(), chunk.id());
            } catch (Exception e) {
                failCount++;
                log.warnv("graph: extraction failed for chunk {0}: {1}", chunk.id(), e.getMessage());
            }
        }
        log.debugv("graph: LLM extraction done — {0} triples from {1} chunks ({2} failures)",
            allTriples.size(), chunks.size(), failCount);

        Map<String, GraphNodeEntity> uniqueNodes = new LinkedHashMap<>();
        Map<String, float[]> nodeEmbeddings = new LinkedHashMap<>();
        List<GraphEdgeEntity> edgesToPersist = new ArrayList<>();

        for (GraphTriple triple : allTriples) {
            var srcNode = uniqueNodes.computeIfAbsent(
                triple.source().toLowerCase(),
                k -> {
                    float[] vec = embeddingClient.embed(triple.source());
                    String id = "node_" + UUID.randomUUID().toString().replace("-", "");
                    nodeEmbeddings.put(id, vec);
                    log.debugv("graph: created source node id={0} name='{1}' dim={2}",
                        id, triple.source(), vec.length);
                    return new GraphNodeEntity(id, null, triple.source(), null, null);
                }
            );
            var tgtNode = uniqueNodes.computeIfAbsent(
                triple.target().toLowerCase(),
                k -> {
                    float[] vec = embeddingClient.embed(triple.target());
                    String id = "node_" + UUID.randomUUID().toString().replace("-", "");
                    nodeEmbeddings.put(id, vec);
                    log.debugv("graph: created target node id={0} name='{1}' dim={2}",
                        id, triple.target(), vec.length);
                    return new GraphNodeEntity(id, null, triple.target(), null, null);
                }
            );

            String edgeId = "edge_" + srcNode.id + "_" + tgtNode.id + "_" + Math.abs(triple.relation().hashCode());
            boolean alreadyExists = edgesToPersist.stream().anyMatch(e -> e.id.equals(edgeId));
            if (!alreadyExists) {
                edgesToPersist.add(new GraphEdgeEntity(
                    edgeId, srcNode.id, tgtNode.id,
                    triple.relation(), 1.0f, triple.description()
                ));
                log.debugv("graph: created edge id={0} src='{1}' -> tgt='{2}' rel='{3}'",
                    edgeId, triple.source(), triple.target(), triple.relation());
            }
        }

        graphNodeRepo.persist(uniqueNodes.values());
        graphEdgeRepo.persist(edgesToPersist);
        log.debugv("graph: persisted {0} nodes, {1} edges", uniqueNodes.size(), edgesToPersist.size());

        int updated = 0;
        for (var entry : nodeEmbeddings.entrySet()) {
            String vecStr = vectorToString(entry.getValue());
            int rows = em.createNativeQuery(
                "UPDATE graph_nodes SET embedding = CAST(:vec AS vector) WHERE id = :id")
                .setParameter("vec", vecStr)
                .setParameter("id", entry.getKey())
                .executeUpdate();
            if (rows > 0) updated++;
        }
        log.debugv("graph: updated embeddings for {0}/{1} nodes in {2}ms",
            updated, nodeEmbeddings.size(), System.currentTimeMillis() - t0);
    }

    public record IngestionResult(int inputPassages, int duplicatesRemoved, int chunksIndexed) {}
}