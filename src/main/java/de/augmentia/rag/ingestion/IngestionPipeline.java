package de.augmentia.rag.ingestion;

import de.augmentia.rag.ai.ContextualizerAiService;
import de.augmentia.rag.ai.EmbeddingModelClient;
import de.augmentia.rag.ai.GraphExtractor;
import de.augmentia.rag.ai.LlmLogger;
import de.augmentia.rag.config.RagConfig;
import de.augmentia.rag.domain.Chunk;
import de.augmentia.rag.domain.GraphTriple;
import de.augmentia.rag.domain.IngestionJobEntity;
import de.augmentia.rag.repository.*;
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

/**
 * 5-step ingestion pipeline: normalize → deduplicate → chunk → contextualize → embed+persist.
 *
 * <p>Optional step 6: graph extraction via LLM. Uses semaphore-based concurrency limiting
 * for LLM calls (max 10 concurrent). Runs asynchronously via Vert.x event bus.
 */
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
    @Inject jakarta.transaction.UserTransaction utx;
    @Inject LlmLogger llmLogger;

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
        log.infov("pipeline: START Job {0} — {1} documents", task.jobId(), task.chunks().size());

        try {
            utx.begin();
            updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.RUNNING, null);

            List<Chunk> current = task.chunks();
            log.infov("pipeline: step 1/5 — normalizing {0} documents", current.size());

            current = current.stream()
                .map(c -> new Chunk(c.id(), c.docId(), c.title(),
                    cleaner.normalize(c.text()), cleaner.normalize(c.contextualText()),
                    c.goldForQuestionIds()))
                .toList();

            Set<String> docIds = current.stream().map(Chunk::docId).collect(Collectors.toSet());
            long existingCount = chunkRepo.count("docId IN ?1", List.copyOf(docIds));

            if (existingCount > 0) {
                long notGraphExtracted = chunkRepo.countNotGraphExtractedByDocIds(docIds);
                if (notGraphExtracted == 0) {
                    log.infov("pipeline: Job {0} SKIPPED — {1} chunks exist, all graph-extracted for docIds {2}",
                        task.jobId(), existingCount, docIds);
                    updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.DONE, null);
                    utx.commit();
                    return;
                }
                if (config.graph().enabled()) {
                    log.infov("pipeline: Job {0} — {1} chunks exist, {2} need graph extraction for docIds {3}",
                        task.jobId(), existingCount, notGraphExtracted, docIds);
                    extractGraphForExistingChunks(docIds);
                    updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.DONE, null);
                    utx.commit();
                    return;
                }
                log.infov("pipeline: Job {0} SKIPPED — {1} chunks exist, graph disabled, {2} need extraction for docIds {3}",
                    task.jobId(), existingCount, notGraphExtracted, docIds);
                updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.DONE, null);
                utx.commit();
                return;
            }

            log.infov("pipeline: step 2/5 — deduplicating");
            var dedupResult = deduper.deduplicate(current);
            current = new ArrayList<>(dedupResult.kept());
            log.infov("pipeline: after dedup: {0} chunks", current.size());

            log.infov("pipeline: step 3/5 — chunking");
            List<Chunk> chunked = new ArrayList<>();
            for (Chunk doc : current) {
                chunked.addAll(chunker.chunk(doc));
            }
            current = chunked;
            log.infov("pipeline: after chunking: {0} chunks", current.size());

            Map<String, String> docLookup = current.stream()
                .collect(Collectors.toMap(Chunk::id, Chunk::text));

            Semaphore semaphore = new Semaphore(MAX_CONCURRENT_LLM_CALLS);

            log.infov("pipeline: step 4/5 — contextualizing {0} chunks (max {1} concurrent LLM calls)",
                current.size(), MAX_CONCURRENT_LLM_CALLS);
            List<Chunk> contextualized = new ArrayList<>(current.size());
            int ctxFailCount = 0;
            for (int ci = 0; ci < current.size(); ci++) {
                Chunk chunk = current.get(ci);
                semaphore.acquire();
                try {
                    String docText = docLookup.getOrDefault(chunk.docId(), chunk.text());
                    String prompt = "Document title: '" + chunk.title() + "'\n<document>\n" + docText + "\n</document>\n\nChunk:\n<chunk>\n" + chunk.text() + "\n</chunk>\n\nGive a short single-sentence context (<=25 words) that situates this chunk within the document.";
                    log.debugv("pipeline: contextualizing chunk[{0}/{1}] id={2}", ci + 1, current.size(), chunk.id());
                    llmLogger.logRequest("Contextualizer", "contextualize", prompt);
                    String context = llmLogger.logAndExecute("Contextualizer", () -> contextualizerAi.contextualize(prompt));
                    log.debugv("pipeline: chunk[{0}] context='{1}'", chunk.id(),
                        context.substring(0, Math.min(80, context.length())));
                    Chunk updated = new Chunk(
                        chunk.id(), chunk.docId(), chunk.title(), chunk.text(),
                        context.isBlank() ? chunk.text() : context + "\n" + chunk.text(),
                        chunk.goldForQuestionIds()
                    );
                    contextualized.add(updated);
                } catch (Exception e) {
                    ctxFailCount++;
                    log.warnv("pipeline: contextualizer FAILED for chunk {0}: {1}", chunk.id(), e.getMessage());
                    log.debugv(e, "pipeline: full contextualizer exception for chunk {0}", chunk.id());
                    contextualized.add(chunk);
                } finally {
                    semaphore.release();
                }
            }
            if (ctxFailCount == current.size() && !current.isEmpty()) {
                throw new RuntimeException(
                    "All " + ctxFailCount + " contextualization calls failed — LLM likely unreachable (check API key / endpoint)");
            }


            List<String> texts = contextualized.stream().map(Chunk::contextualText).toList();
            log.infov("pipeline: step 5b/5 — generating embeddings for {0} texts", texts.size());
            List<float[]> embeddings = embeddingClient.embedBatch(texts);
            log.infov("pipeline: embeddings generated: {0} vectors of dim {1}",
                embeddings.size(), embeddings.isEmpty() ? 0 : embeddings.get(0).length);

            log.infov("pipeline: step 5c/5 — persisting chunks to database");
            persistChunksAndEmbeddingsBulk(contextualized, embeddings);

            if (config.graph().enabled()) {
                log.infov("pipeline: step 5a/5 — graph extraction (enabled)");
                extractGraph(contextualized);
            } else {
                log.infov("pipeline: step 5a/5 — graph extraction SKIPPED (disabled)");
            }

            updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.DONE, null);
            utx.commit();
            log.infov("pipeline: DONE Job {0}", task.jobId());
        } catch (Exception e) {
            log.errorv(e, "pipeline: FAILED Job {0}", task.jobId());
            try { utx.rollback(); } catch (Exception ignored) {}
            updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.FAILED, e.getMessage());
        }
    }

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

    protected void persistChunksAndEmbeddingsBulk(List<Chunk> chunks, List<float[]> embeddings) {
        String[] ids = new String[chunks.size()];
        String[] vectorStrings = new String[chunks.size()];

        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            ids[i] = chunk.id();
            vectorStrings[i] = Arrays.toString(embeddings.get(i));

            em.createNativeQuery("""
                INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, graph_extracted)
                VALUES (:id, :docId, :title, :raw, :ctx, FALSE)
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

    private record ChunkTriples(Chunk chunk, List<GraphTriple> triples) {}

    private void extractGraph(List<Chunk> chunks) {
        long t0 = System.currentTimeMillis();
        log.infov("graph: START — extracting from {0} chunks", chunks.size());
        List<ChunkTriples> allPerChunk = new ArrayList<>();
        List<String> succeededChunkIds = new ArrayList<>();
        int failCount = 0;
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            try {
                String ctxText = chunk.contextualText();
                log.debugv("graph: chunk[{0}/{1}] id={2} text_preview='{3}'",
                    i + 1, chunks.size(), chunk.id(),
                    ctxText.substring(0, Math.min(80, ctxText.length())));
                llmLogger.logRequest("GraphExtractor", "extract", ctxText);
                String raw = llmLogger.logAndExecute("GraphExtractor", () -> graphExtractor.extract(ctxText));
                log.debugv("graph: chunk[{0}] LLM raw response (len={1}): {2}",
                    chunk.id(), raw.length(),
                    raw.substring(0, Math.min(200, raw.length())));
                List<GraphTriple> parsed = JSON.readValue(raw,
                    new TypeReference<List<GraphTriple>>() {});
                allPerChunk.add(new ChunkTriples(chunk, parsed));
                succeededChunkIds.add(chunk.id());
                log.infov("graph: chunk[{0}] → {1} triples: {2}",
                    chunk.id(), parsed.size(), parsed);
            } catch (Exception e) {
                failCount++;
                log.warnv("graph: EXTRACTION FAILED for chunk {0}: {1}", chunk.id(), e.getMessage());
                log.debugv(e, "graph: full exception for chunk {0}", chunk.id());
            }
        }
        int totalTriples = allPerChunk.stream().mapToInt(ct -> ct.triples().size()).sum();
        log.infov("graph: LLM extraction done — {0} triples from {1} chunks ({2} failures) in {3}ms",
            totalTriples, chunks.size(), failCount, System.currentTimeMillis() - t0);

        if (failCount == chunks.size() && !chunks.isEmpty()) {
            String msg = "All " + failCount + " graph extractions failed — LLM likely unreachable (check API key / endpoint)";
            log.errorv("graph: {0}", msg);
            throw new RuntimeException(msg);
        }

        Map<String, GraphNodeEntity> uniqueNodes = new LinkedHashMap<>();
        Map<String, float[]> nodeEmbeddings = new LinkedHashMap<>();
        List<GraphEdgeEntity> edgesToPersist = new ArrayList<>();

        for (ChunkTriples ct : allPerChunk) {
            String chunkId = ct.chunk().id();
            for (GraphTriple triple : ct.triples()) {
                log.debugv("graph: processing triple — {0} --[{1}]--> {2} (chunk={3})",
                    triple.source(), triple.relation(), triple.target(), chunkId);
                var srcNode = uniqueNodes.computeIfAbsent(
                    triple.source().toLowerCase(),
                    k -> {
                        log.debugv("graph: creating NEW node for '{0}'", triple.source());
                        float[] vec = embeddingClient.embed(triple.source());
                        String id = "node_" + UUID.randomUUID().toString().replace("-", "");
                        nodeEmbeddings.put(id, vec);
                        return new GraphNodeEntity(id, chunkId, triple.source(), null, null);
                    }
                );
                var tgtNode = uniqueNodes.computeIfAbsent(
                    triple.target().toLowerCase(),
                    k -> {
                        log.debugv("graph: creating NEW node for '{0}'", triple.target());
                        float[] vec = embeddingClient.embed(triple.target());
                        String id = "node_" + UUID.randomUUID().toString().replace("-", "");
                        nodeEmbeddings.put(id, vec);
                        return new GraphNodeEntity(id, chunkId, triple.target(), null, null);
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
        }

        log.infov("graph: persisting {0} nodes, {1} edges", uniqueNodes.size(), edgesToPersist.size());
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

        em.createNativeQuery(
            "UPDATE rag_chunks SET graph_extracted = TRUE WHERE id IN :ids")
            .setParameter("ids", succeededChunkIds)
            .executeUpdate();
        log.infov("graph: marked {0}/{1} chunks as graph_extracted", succeededChunkIds.size(), chunks.size());

        log.infov("graph: DONE — persisted {0} nodes + embeddings in {1}ms",
            nodeEmbeddings.size(), System.currentTimeMillis() - t0);
    }

    private void extractGraphForExistingChunks(Set<String> docIds) {
        long t0 = System.currentTimeMillis();
        List<ChunkEntity> entities = chunkRepo.findByDocIdsNotGraphExtracted(docIds);
        List<Chunk> chunks = entities.stream().map(ChunkEntity::toDomain).toList();
        log.infov("graph: backfill START — {0} chunks need extraction for docIds {1}", chunks.size(), docIds);
        if (chunks.isEmpty()) return;
        extractGraph(chunks);
        log.infov("graph: backfill DONE in {0}ms", System.currentTimeMillis() - t0);
    }

    public record IngestionTask(UUID jobId, List<Chunk> chunks) {}
}
