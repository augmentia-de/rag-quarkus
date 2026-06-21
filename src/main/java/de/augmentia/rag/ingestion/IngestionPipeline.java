package de.augmentia.rag.ingestion;

import de.augmentia.rag.ai.ContextualizerAiService;
import de.augmentia.rag.ai.EmbeddingModelClient;
import de.augmentia.rag.ai.GraphExtractor;
import de.augmentia.rag.config.RagConfig;
import de.augmentia.rag.domain.Chunk;
import de.augmentia.rag.domain.GraphTriple;
import de.augmentia.rag.domain.IngestionJobEntity;
import de.augmentia.rag.repository.ChunkRepository;
import de.augmentia.rag.repository.GraphEdgeEntity;
import de.augmentia.rag.repository.GraphEdgeRepository;
import de.augmentia.rag.repository.GraphNodeEntity;
import de.augmentia.rag.repository.GraphNodeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.Semaphore;

@ApplicationScoped
public class IngestionPipeline {

    private static final Logger log = Logger.getLogger(IngestionPipeline.class);
    private static final int MAX_CONCURRENT_LLM_CALLS = 10;

    @Inject EventBus eventBus;
    @Inject CorpusCleaner cleaner;
    @Inject NearDuplicateDeduper deduper;
    @Inject StructureAwareChunker chunker;
    @Inject ContextualizerAiService contextualizerAi;
    @Inject GraphExtractor graphExtractor;
    @Inject EmbeddingModelClient embeddingClient;
    @Inject ChunkRepository chunkRepo;
    @Inject GraphNodeRepository graphNodeRepo;
    @Inject GraphEdgeRepository graphEdgeRepo;
    @Inject RagConfig config;
    @Inject EntityManager em;

    public UUID submitForIngestion(List<Chunk> rawDocuments) {
        UUID jobId = UUID.randomUUID();
        createJobRecord(jobId, rawDocuments.size());
        eventBus.send("ingest.process.v1", new IngestionTask(jobId, rawDocuments));
        return jobId;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void createJobRecord(UUID jobId, int size) {
        IngestionJobEntity job = new IngestionJobEntity();
        job.id = jobId;
        job.status = IngestionJobEntity.JobStatus.PENDING;
        job.totalChunks = size;
        job.createdAt = OffsetDateTime.now();
        job.updatedAt = OffsetDateTime.now();
        job.persist();
    }

    @io.quarkus.vertx.ConsumeEvent(value = "ingest.process.v1", blocking = true)
    public void processAsynchronously(IngestionTask task) {
        updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.RUNNING, null);
        log.infov("Starte Verarbeitung für Job {0}", task.jobId());

        try {
            List<Chunk> current = task.chunks();

            current = current.stream()
                .map(c -> new Chunk(c.id(), c.docId(), c.title(),
                    cleaner.normalize(c.text()), cleaner.normalize(c.contextualText()),
                    c.goldForQuestionIds()))
                .toList();

            var dedupResult = deduper.deduplicate(current);
            current = new ArrayList<>(dedupResult.kept());

            List<Chunk> chunked = new ArrayList<>();
            for (Chunk doc : current) {
                chunked.addAll(chunker.chunk(doc));
            }
            current = chunked;

            Map<String, String> docLookup = current.stream()
                .collect(Collectors.toMap(Chunk::id, Chunk::text));

            Semaphore semaphore = new Semaphore(MAX_CONCURRENT_LLM_CALLS);

            List<Chunk> contextualized = new ArrayList<>(current.size());
            for (Chunk chunk : current) {
                semaphore.acquire();
                try {
                    String docText = docLookup.getOrDefault(chunk.docId(), chunk.text());
                    String prompt = "Document title: '" + chunk.title() + "'\n<document>\n" + docText + "\n</document>\n\nChunk:\n<chunk>\n" + chunk.text() + "\n</chunk>\n\nGive a short single-sentence context (<=25 words) that situates this chunk within the document.";
                    String context = contextualizerAi.contextualize(prompt);
                    Chunk updated = new Chunk(
                        chunk.id(), chunk.docId(), chunk.title(), chunk.text(),
                        context.isBlank() ? chunk.text() : context + "\n" + chunk.text(),
                        chunk.goldForQuestionIds()
                    );
                    contextualized.add(updated);
                } catch (Exception e) {
                    log.warnv("contextualizer: failed for chunk {0}: {1}", chunk.id(), e.getMessage());
                    contextualized.add(chunk);
                } finally {
                    semaphore.release();
                }
            }

            if (config.graph().enabled()) {
                extractGraph(contextualized);
            }

            List<String> texts = contextualized.stream().map(Chunk::contextualText).toList();
            List<float[]> embeddings = embeddingClient.embedBatch(texts);

            persistChunksAndEmbeddingsBulk(contextualized, embeddings);

            updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.DONE, null);
        } catch (Exception e) {
            log.errorv(e, "Fehler in Ingestion Pipeline für Job {0}", task.jobId());
            updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.FAILED, e.getMessage());
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void updateJobStatus(UUID jobId, IngestionJobEntity.JobStatus status, String error) {
        IngestionJobEntity job = IngestionJobEntity.findById(jobId);
        if (job != null) {
            job.status = status;
            if (status == IngestionJobEntity.JobStatus.DONE) {
                job.processedChunks = job.totalChunks;
            }
            if (error != null) {
                job.errorMessage = error;
            }
            job.persist();
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void persistChunksAndEmbeddingsBulk(List<Chunk> chunks, List<float[]> embeddings) {
        String[] ids = new String[chunks.size()];
        String[] vectorStrings = new String[chunks.size()];

        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            ids[i] = chunk.id();
            vectorStrings[i] = Arrays.toString(embeddings.get(i));

            em.createNativeQuery("""
                INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text)
                VALUES (:id, :docId, :title, :raw, :ctx)
                ON CONFLICT (id) DO UPDATE SET contextual_text = EXCLUDED.contextual_text
                """)
              .setParameter("id", chunk.id())
              .setParameter("docId", chunk.docId())
              .setParameter("title", chunk.title())
              .setParameter("raw", chunk.text())
              .setParameter("ctx", chunk.contextualText())
              .executeUpdate();
        }

        em.createNativeQuery("""
            UPDATE rag_chunks
            SET embedding = CAST(data.vec AS vector)
            FROM (
                SELECT unnest(:ids)::varchar AS id,
                       unnest(:vecs)::varchar AS vec
            ) AS data
            WHERE rag_chunks.id = data.id
            """)
          .setParameter("ids", ids)
          .setParameter("vecs", vectorStrings)
          .executeUpdate();
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
                    return new GraphNodeEntity(id, null, triple.source(), null, null);
                }
            );
            var tgtNode = uniqueNodes.computeIfAbsent(
                triple.target().toLowerCase(),
                k -> {
                    float[] vec = embeddingClient.embed(triple.target());
                    String id = "node_" + UUID.randomUUID().toString().replace("-", "");
                    nodeEmbeddings.put(id, vec);
                    return new GraphNodeEntity(id, null, triple.target(), null, null);
                }
            );

            String edgeId = "edge_" + srcNode.id + "_" + tgtNode.id + "_" + Math.abs(triple.relation().hashCode());
            boolean alreadyExists = edgesToPersist.stream().anyMatch(e -> e.id.equals(edgeId));
            if (!alreadyExists) {
                edgesToPersist.add(new GraphEdgeEntity(
                    edgeId, srcNode.id, tgtNode.id, triple.relation(), 1.0f, triple.description()
                ));
            }
        }

        graphNodeRepo.persist(uniqueNodes.values());
        graphEdgeRepo.persist(edgesToPersist);

        for (var entry : nodeEmbeddings.entrySet()) {
            String vecStr = Arrays.toString(entry.getValue());
            em.createNativeQuery(
                "UPDATE graph_nodes SET embedding = CAST(:vec AS vector) WHERE id = :id")
                .setParameter("vec", vecStr)
                .setParameter("id", entry.getKey())
                .executeUpdate();
        }
    }

    public record IngestionTask(UUID jobId, List<Chunk> chunks) {}
}
