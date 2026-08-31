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
    @Inject FusedChunkProcessor fusedChunkProcessor;
    @Inject EntityCanonicalizer entityCanonicalizer;
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

    /**
     * Main ingestion worker. Transaction management is deliberately split:
     * <ul>
     *   <li>Job status writes each get their own short-lived transaction via {@link #updateJobStatus}.</li>
     *   <li>LLM calls + embedding run <b>without</b> an open transaction to avoid the 300s JTA
     *       timeout (previously caused {@code TransactionRequiredException} for large batches).</li>
     *   <li>A single final transaction wraps only the DB-heavy writes
     *       ({@link #persistChunksAndEmbeddingsBulk} + {@link #persistGraphData}).</li>
     * </ul>
     */
    @io.quarkus.vertx.ConsumeEvent(value = "ingest.process.v1", blocking = true)
    public void processAsynchronously(IngestionTask task) {
        log.infov("pipeline: START Job {0} — {1} documents", task.jobId(), task.chunks().size());

        try {
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
                    return;
                }
                if (config.graph().enabled()) {
                    log.infov("pipeline: Job {0} — {1} chunks exist, {2} need graph extraction for docIds {3}",
                        task.jobId(), existingCount, notGraphExtracted, docIds);
                    extractGraphForExistingChunks(docIds);
                    updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.DONE, null);
                    return;
                }
                log.infov("pipeline: Job {0} SKIPPED — {1} chunks exist, graph disabled, {2} need extraction for docIds {3}",
                    task.jobId(), existingCount, notGraphExtracted, docIds);
                updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.DONE, null);
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

            boolean fused = config.graph().enabled();
            log.infov("pipeline: step 4/5 — contextualizing {0} chunks (max {1} concurrent LLM calls, fused={2})",
                current.size(), MAX_CONCURRENT_LLM_CALLS, fused);
            List<Chunk> contextualized = new ArrayList<>(current.size());
            Map<String, List<GraphTriple>> fusedTriplesByChunk = new LinkedHashMap<>();
            Set<String> fusedOkChunkIds = new LinkedHashSet<>();
            int ctxFailCount = 0;
            for (int ci = 0; ci < current.size(); ci++) {
                Chunk chunk = current.get(ci);
                semaphore.acquire();
                try {
                    String docText = docLookup.getOrDefault(chunk.docId(), chunk.text());
                    if (fused) {
                        log.debugv("pipeline: fused analyzing chunk[{0}/{1}] id={2}", ci + 1, current.size(), chunk.id());
                        var result = fusedChunkProcessor.process(chunk.title(), docText, chunk.text());
                        fusedTriplesByChunk.put(chunk.id(), result.triples());
                        if (result.ok()) {
                            fusedOkChunkIds.add(chunk.id());
                        } else {
                            ctxFailCount++;
                        }
                        Chunk updated = new Chunk(
                            chunk.id(), chunk.docId(), chunk.title(), chunk.text(),
                            result.contextualText(),
                            chunk.goldForQuestionIds()
                        );
                        contextualized.add(updated);
                    } else {
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
                    }
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
            try {
                utx.begin();
                persistChunksAndEmbeddingsBulk(contextualized, embeddings);
                utx.commit();
            } catch (Exception txEx) {
                try { utx.rollback(); } catch (Exception ignored) {}
                throw txEx;
            }

            if (config.graph().enabled()) {
                log.infov("pipeline: step 5a/5 — graph persistence (fused extraction, enabled)");
                List<ChunkTriples> allPerChunk = contextualized.stream()
                    .filter(c -> fusedOkChunkIds.contains(c.id()))
                    .map(c -> new ChunkTriples(c, fusedTriplesByChunk.getOrDefault(c.id(), List.of())))
                    .toList();
                List<String> succeededChunkIds = allPerChunk.stream().map(ct -> ct.chunk().id()).toList();
                log.infov("pipeline: computing graph nodes, embeddings, and edges (no tx)");
                GraphPersistData graphData = computeGraphTriples(allPerChunk, succeededChunkIds);
                try {
                    utx.begin();
                    persistGraphData(graphData);
                    utx.commit();
                } catch (Exception txEx) {
                    try { utx.rollback(); } catch (Exception ignored) {}
                    throw txEx;
                }
            } else {
                log.infov("pipeline: step 5a/5 — graph extraction SKIPPED (disabled)");
            }

            updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.DONE, null);
            log.infov("pipeline: DONE Job {0}", task.jobId());
        } catch (Exception e) {
            log.errorv(e, "pipeline: FAILED Job {0}", task.jobId());
            try { utx.rollback(); } catch (Exception ignored) {}
            updateJobStatus(task.jobId(), IngestionJobEntity.JobStatus.FAILED, e.getMessage());
        }
    }

    /**
     * Updates the ingestion job status in its own short transaction.
     * Safe to call at any point in the pipeline — it never reuses the outer transaction.
     */
    protected void updateJobStatus(UUID jobId, IngestionJobEntity.JobStatus status, String error) {
        try {
            utx.begin();
        } catch (Exception e) {
            log.warnv("updateJobStatus: utx.begin() failed for {0}: {1}", jobId, e.getMessage());
            return;
        }
        try {
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
            utx.commit();
        } catch (Exception e) {
            log.warnv("updateJobStatus: commit failed for {0}: {1}", jobId, e.getMessage());
            try { utx.rollback(); } catch (Exception ignored) {}
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

    /**
     * Result of graph triple computation. Embeddings are pre-computed so that
     * the subsequent DB persist call can run inside a short transaction.
     */
    private record GraphPersistData(
        Map<String, GraphNodeEntity> uniqueNodes,
        Map<String, float[]> nodeEmbeddings,
        List<GraphEdgeEntity> edgesToPersist,
        List<String> succeededChunkIds
    ) {}

    /**
     * Computes unique nodes, their embeddings, and edges from extracted triples.
     * This involves LLM/embedding calls and must NOT run inside a transaction.
     */
    private GraphPersistData computeGraphTriples(List<ChunkTriples> allPerChunk, List<String> succeededChunkIds) {
        Map<String, GraphNodeEntity> uniqueNodes = new LinkedHashMap<>();
        Map<String, float[]> nodeEmbeddings = new LinkedHashMap<>();
        List<GraphEdgeEntity> edgesToPersist = new ArrayList<>();

        for (ChunkTriples ct : allPerChunk) {
            String chunkId = ct.chunk().id();
            for (GraphTriple triple : ct.triples()) {
                String canonicalSrc = entityCanonicalizer.canonicalize(triple.source());
                String canonicalTgt = entityCanonicalizer.canonicalize(triple.target());
                var srcNode = uniqueNodes.computeIfAbsent(
                    canonicalSrc,
                    k -> {
                        log.debugv("graph: creating NEW node for '{0}'", canonicalSrc);
                        float[] vec = embeddingClient.embed(k);
                        String id = "node_" + UUID.randomUUID().toString().replace("-", "");
                        nodeEmbeddings.put(id, vec);
                        return new GraphNodeEntity(id, chunkId, k, null, null);
                    }
                );
                var tgtNode = uniqueNodes.computeIfAbsent(
                    canonicalTgt,
                    k -> {
                        log.debugv("graph: creating NEW node for '{0}'", canonicalTgt);
                        float[] vec = embeddingClient.embed(k);
                        String id = "node_" + UUID.randomUUID().toString().replace("-", "");
                        nodeEmbeddings.put(id, vec);
                        return new GraphNodeEntity(id, chunkId, k, null, null);
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
        return new GraphPersistData(uniqueNodes, nodeEmbeddings, edgesToPersist, succeededChunkIds);
    }

    /**
     * Persists pre-computed graph nodes, edges, and embeddings. Runs ONLY DB writes
     * (no LLM/embedding calls) — safe to call inside a short transaction.
     */
    private void persistGraphData(GraphPersistData data) {
        log.infov("graph: persisting {0} nodes, {1} edges", data.uniqueNodes().size(), data.edgesToPersist().size());
        graphNodeRepo.persist(data.uniqueNodes().values());
        graphEdgeRepo.persist(data.edgesToPersist());

        for (var entry : data.nodeEmbeddings().entrySet()) {
            String vecStr = Arrays.toString(entry.getValue());
            em.createNativeQuery(
                "UPDATE graph_nodes SET embedding = CAST(:vec AS vector) WHERE id = :id")
                .setParameter("vec", vecStr)
                .setParameter("id", entry.getKey())
                .executeUpdate();
        }

        em.createNativeQuery(
            "UPDATE rag_chunks SET graph_extracted = TRUE WHERE id IN :ids")
            .setParameter("ids", data.succeededChunkIds())
            .executeUpdate();
        log.infov("graph: marked {0} chunks as graph_extracted", data.succeededChunkIds().size());
    }

    /**
     * Legacy entry point: extracts graph via LLM for already-existing chunks.
     * Called when some chunks lack graph_extracted=true.
     * LLM calls run outside the transaction; only DB writes are transactional.
     */
    private void extractGraphForExistingChunks(Set<String> docIds) {
        long t0 = System.currentTimeMillis();
        List<ChunkEntity> entities = chunkRepo.findByDocIdsNotGraphExtracted(docIds);
        List<Chunk> chunks = entities.stream().map(ChunkEntity::toDomain).toList();
        log.infov("graph: backfill START — {0} chunks need extraction for docIds {1}", chunks.size(), docIds);
        if (chunks.isEmpty()) return;

        // Phase 1: LLM extraction (no tx) — slow per-chunk LLM calls
        List<ChunkTriples> allPerChunk = new ArrayList<>();
        List<String> succeededChunkIds = new ArrayList<>();
        int failCount = 0;
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            try {
                String ctxText = chunk.contextualText();
                llmLogger.logRequest("GraphExtractor", "extract", ctxText);
                String raw = llmLogger.logAndExecute("GraphExtractor", () -> graphExtractor.extract(ctxText));
                List<GraphTriple> parsed = JSON.readValue(raw,
                    new TypeReference<List<GraphTriple>>() {});
                allPerChunk.add(new ChunkTriples(chunk, parsed));
                succeededChunkIds.add(chunk.id());
            } catch (Exception e) {
                failCount++;
                log.warnv("graph: EXTRACTION FAILED for chunk {0}: {1}", chunk.id(), e.getMessage());
            }
        }
        if (failCount == chunks.size() && !chunks.isEmpty()) {
            throw new RuntimeException("All " + failCount + " graph extractions failed — LLM unreachable");
        }

        // Phase 2: compute node embeddings (no tx) — slow embedding calls
        GraphPersistData graphData = computeGraphTriples(allPerChunk, succeededChunkIds);

        // Phase 3: DB writes (short tx)
        try {
            utx.begin();
            persistGraphData(graphData);
            utx.commit();
        } catch (Exception txEx) {
            try { utx.rollback(); } catch (Exception ignored) {}
            throw new RuntimeException("graph persistence failed", txEx);
        }
        log.infov("graph: backfill DONE in {0}ms", System.currentTimeMillis() - t0);
    }

    public record IngestionTask(UUID jobId, List<Chunk> chunks) {}
}
