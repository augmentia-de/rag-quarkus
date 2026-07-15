package de.augmentia.rag.engine;

import de.augmentia.rag.ai.EmbeddingModelClient;
import de.augmentia.rag.ai.GeneratorAiService;
import de.augmentia.rag.config.RagConfig;
import de.augmentia.rag.domain.*;
import de.augmentia.rag.domain.*;
import de.augmentia.rag.repository.ChunkEntity;
import de.augmentia.rag.repository.ChunkRepository;
import de.augmentia.rag.repository.FullTextSearchRepository;
import de.augmentia.rag.repository.SearchResult;
import de.augmentia.rag.repository.VectorSearchRepository;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core RAG engine implementing the defensively grounded pipeline.
 *
 * <p>Pipeline flow: route → retrieve → graph-augment → generate → decompose-claims → verify → CRAG-loop.
 * Returns ABSTAIN when confidence is too low or verification fails after all hops.
 *
 * @see ReciprocalRankFusion
 * @see CrossEncoderReranker
 * @see FaithfulnessJudge
 */
@ApplicationScoped
public class DefensivelyGroundedRagEngine {

    private static final Logger log = Logger.getLogger(DefensivelyGroundedRagEngine.class);

    @Inject VectorSearchRepository vectorStore;
    @Inject FullTextSearchRepository fullTextStore;
    @Inject ChunkRepository chunkRepo;
    @Inject ReciprocalRankFusion rrf;
    @Inject CrossEncoderReranker reranker;
    @Inject ClaimDecomposer claimDecomposer;
    @Inject FaithfulnessJudge faithfulnessJudge;
    @Inject QueryRouter queryRouter;
    @Inject GeneratorAiService generator;
    @Inject EmbeddingModelClient embeddingClient;
    @Inject RagConfig config;
    @Inject GraphSearchService graphSearchService;
    @Inject de.augmentia.rag.ai.LlmLogger llmLogger;

    /**
     * Wraps pipeline execution in a Uni for async/non-blocking use.
     */
    public Uni<RagResponse> processQuery(RagQuery query) {
        return Uni.createFrom().item(() -> executePipeline(query));
    }

    /**
     * Core pipeline: route → retrieve → graph-augment → generate → verify → CRAG-loop.
     *
     * <p>Returns ABSTAIN when: no context found, generator abstains, or verification
     * fails after max CRAG hops. Graph augmentation adds chunks from connected entities
     * when enabled.
     */
    RagResponse executePipeline(RagQuery query) {
        log.debugv("Processing query: {0}", query.question());
        long start = System.nanoTime();

        var routed = queryRouter.route(query);
        List<Chunk> context = retrieve(routed);

        if (config.graph().enabled()) {
            log.debugv("Graph retrieval enabled, augmenting with graph search");
            var graphResult = graphSearchService.search(query.question(), config.graph().hops(), config.graph().maxNodes());
            Set<String> existingIds = context.stream().map(Chunk::id).collect(Collectors.toSet());
            List<Chunk> graphOnly = graphResult.contextChunks().stream()
                .filter(c -> !existingIds.contains(c.id()))
                .toList();
            if (!graphOnly.isEmpty()) {
                List<Chunk> combined = new ArrayList<>(context);
                combined.addAll(graphOnly);
                context = combined;
                log.infov("Graph augmented: {0} vector + {1} graph = {2} total",
                    context.size() - graphOnly.size(), graphOnly.size(), context.size());
            }
        }

        if (!context.isEmpty()) {
            log.infov("Retrieved {0} chunks: {1}", context.size(), context.stream().map(Chunk::id).toList());
        }

        if (context.isEmpty()) {
            log.warnv("No context retrieved for query: {0}", query.question());
            return RagResponse.abstain();
        }

        long tGen = System.nanoTime();
        String rawAnswer = generateCitedAnswer(query.question(), context);
        log.debugv("executePipeline: generation took {0}ms",
            (System.nanoTime() - tGen) / 1_000_000);

        if (rawAnswer.contains("ABSTAIN") || rawAnswer.isBlank()) {
            log.infov("Generator abstained for query: {0}", query.question());
            return RagResponse.abstain();
        }

        List<AtomicClaim> claims = claimDecomposer.decompose(rawAnswer);
        log.infov("Generator answer: {0}", rawAnswer);
        log.infov("Decomposed claims: {0}", claims);
        VerificationResult verification = faithfulnessJudge.verify(claims, context);

        if (!verification.isFaithful()) {
            log.warnv("Verification failed for query: {0}, unverified: {1}, scores: {2}",
                query.question(), verification.unverifiedClaims().size(), verification.claimScores());
            return cragLoop(query, context, verification);
        }

        double retrievalConfidence = computeConfidence(context);
        if (retrievalConfidence < config.crag().thresholdOk()) {
            log.warnv("Low retrieval confidence ({0}) for query: {1}",
                retrievalConfidence, query.question());
            return RagResponse.abstain();
        }

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        log.infov("Query processed in {0}ms: {1}", elapsed, query.question());

        return new RagResponse(rawAnswer, false, extractCitationIds(rawAnswer), List.of(verification));
    }

    public List<Chunk> retrieve(String query, int topK) {
        var routed = queryRouter.route(new RagQuery(query, null, topK, true));
        return retrieve(routed);
    }

    public List<ScoredChunk> retrieveWithScores(String query, int topK) {
        var routed = queryRouter.route(new RagQuery(query, null, topK, true));
        return retrieveWithScores(routed);
    }

    public GraphSearchResult processGraphQuery(String question, int hops, int maxNodes) {
        return graphSearchService.search(question, hops, maxNodes);
    }

    @CacheResult(cacheName = "rag-retrieval")
    List<Chunk> retrieve(QueryRouter.RoutedQuery routed) {
        return retrieveWithScores(routed).stream()
            .map(sc -> new Chunk(sc.id(), sc.docId(), sc.title(), sc.text(), sc.contextualText(), List.of()))
            .toList();
    }

    List<ScoredChunk> retrieveWithScores(QueryRouter.RoutedQuery routed) {
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        long start = System.nanoTime();

        int totalChunks = (int) chunkRepo.count();
        int effectiveTopK = Math.min(config.retrieve().topK(), Math.max(1, totalChunks));

        log.debugv("retrieve: starting with {0} sub-questions, totalChunks={1}, effectiveTopK={2}",
            routed.subQuestions().size(), totalChunks, effectiveTopK);

        for (String subQ : routed.subQuestions()) {
            log.debugv("retrieve: processing sub-question '{0}'", subQ);
            float[] queryVec = embeddingClient.embed(subQ);

            var denseResults = vectorStore.search(queryVec, effectiveTopK);
            var sparseResults = fullTextStore.search(subQ, effectiveTopK);

            double maxDense = denseResults.stream()
                .mapToDouble(SearchResult::getScore).max().orElse(0.0);
            double threshold = maxDense * config.retrieve().similarityRatio();
            denseResults = denseResults.stream()
                .filter(r -> r.getScore() >= threshold).toList();
            log.debugv("retrieve: dense threshold={0} (max={1}, ratio={2}), filtered={3}",
                String.format("%.4f", threshold), String.format("%.4f", maxDense),
                config.retrieve().similarityRatio(), denseResults.size());

            for (SearchResult sr : denseResults) {
                scoreMap.merge(sr.getChunkId(), (double) sr.getScore(), Math::max);
            }

            List<String> fusedIds = rrf.fuse(denseResults, sparseResults, config.retrieve().rrfK());
            log.debugv("retrieve: sub-question '{0}' → dense={1} sparse={2} fused={3}",
                subQ, denseResults.size(), sparseResults.size(), fusedIds.size());
        }

        List<Chunk> candidates = scoreMap.keySet().stream()
            .map(id -> chunkRepo.findById(id).map(ChunkEntity::toDomain).orElse(null))
            .filter(Objects::nonNull)
            .toList();
        log.debugv("retrieve: fetched {0} candidates from DB", candidates.size());

        if (routed.type() == QuestionType.COMPARISON) {
            int before = candidates.size();
            candidates = deduplicateByTitle(candidates);
            log.debugv("retrieve: comparison dedup {0} → {1}", before, candidates.size());
        }

        List<Chunk> ranked = reranker.rerank(routed.original(), candidates, config.rerank().topN());
        Set<String> rankedIds = ranked.stream().map(Chunk::id).collect(Collectors.toSet());

        List<ScoredChunk> result = ranked.stream()
            .map(c -> new ScoredChunk(c, scoreMap.getOrDefault(c.id(), 0.0)))
            .toList();

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        log.debugv("retrieve: done in {0}ms — {1} candidates → {2} final chunks",
            elapsed, candidates.size(), result.size());

        return result;
    }

    /**
     * Generates a cited answer from context chunks.
     * Format: "[ID: chunk-id] sentence text" for each claim.
     */
    String generateCitedAnswer(String question, List<Chunk> context) {
        StringBuilder contextBlock = new StringBuilder();
        for (Chunk c : context) {
            contextBlock.append(String.format("[ID: %s] %s%n%n", c.id(), c.contextualText()));
        }
        String input = "CONTEXT:\n" + contextBlock + "\nQUERY: " + question;
        llmLogger.logRequest("Generator", "generate", input);
        String response = llmLogger.logAndExecute("Generator", () -> generator.generate(contextBlock.toString(), question));
        return normalizeCitations(response);
    }

    static String normalizeCitations(String answer) {
        return answer.replace('\u3010', '[').replace('\u3011', ']');
    }

    /**
     * Corrective RAG loop: reformulate query from unverified claims, re-retrieve,
     * re-generate, re-verify. Exits when faithfulness passes and confidence >= thresholdOk.
     */
    RagResponse cragLoop(RagQuery query, List<Chunk> previousContext, VerificationResult failedVerification) {
        List<Chunk> allContext = new ArrayList<>(previousContext);

        for (int hop = 0; hop < config.crag().maxHops(); hop++) {
            log.debugv("CRAG hop {0} for query: {1}", hop + 1, query.question());

            String reformulated = reformulateQuery(query.question(), failedVerification);
            var routed = queryRouter.route(new RagQuery(reformulated, query.userId(), config.rerank().topN(), true));
            List<Chunk> newContext = retrieve(routed);

            Set<String> seenIds = allContext.stream().map(Chunk::id).collect(Collectors.toSet());
            for (Chunk c : newContext) {
                if (!seenIds.contains(c.id())) {
                    allContext.add(c);
                }
            }

            String newAnswer = generateCitedAnswer(query.question(), allContext);
            if (newAnswer.contains("ABSTAIN") || newAnswer.isBlank()) {
                return RagResponse.abstain();
            }

            List<AtomicClaim> claims = claimDecomposer.decompose(newAnswer);
            VerificationResult newVerification = faithfulnessJudge.verify(claims, allContext);

            if (newVerification.isFaithful()) {
                double retrievalConfidence = computeConfidence(allContext);
                if (retrievalConfidence >= config.crag().thresholdOk()) {
                    log.infov("CRAG recovered at hop {0}", hop + 1);
                    return new RagResponse(newAnswer, false, extractCitationIds(newAnswer), List.of(newVerification));
                }
            }
            failedVerification = newVerification;
        }

        return RagResponse.abstain();
    }

    /**
     * Appends unverified claims to the original question for re-retrieval.
     */
    private String reformulateQuery(String original, VerificationResult failed) {
        return original + " (reformulated: " + String.join("; ", failed.unverifiedClaims()) + ")";
    }

    /**
     * Heuristic: chunkCount / topN, clamped to [0, 1]. Higher when more context is available.
     */
    private double computeConfidence(List<Chunk> context) {
        if (context.isEmpty()) return 0.0;
        return Math.min(1.0, context.size() / (double) config.rerank().topN());
    }

    /**
     * Extracts chunk IDs from [ID: xxx] or [xxx] citation brackets in the answer.
     */
    private List<String> extractCitationIds(String answer) {
        var matcher = java.util.regex.Pattern.compile("\\[([^\\]]+)\\]").matcher(answer);
        List<String> ids = new ArrayList<>();
        while (matcher.find()) {
            String content = matcher.group(1).trim();
            if (content.startsWith("http")) continue;
            ids.add(content.replaceFirst("(?i)^ID:\\s*", ""));
        }
        return ids;
    }

    private List<Chunk> deduplicateByTitle(List<Chunk> chunks) {
        Set<String> seenTitles = new HashSet<>();
        List<Chunk> deduped = new ArrayList<>();
        for (Chunk c : chunks) {
            if (seenTitles.add(c.title())) {
                deduped.add(c);
            }
        }
        return deduped;
    }
}