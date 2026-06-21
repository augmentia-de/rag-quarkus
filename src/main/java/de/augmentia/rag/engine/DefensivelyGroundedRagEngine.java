package de.augmentia.rag.engine;

import de.augmentia.rag.ai.EmbeddingModelClient;
import de.augmentia.rag.ai.GeneratorAiService;
import de.augmentia.rag.config.RagConfig;
import de.augmentia.rag.domain.*;
import de.augmentia.rag.domain.*;
import de.augmentia.rag.repository.ChunkEntity;
import de.augmentia.rag.repository.ChunkRepository;
import de.augmentia.rag.repository.FullTextSearchRepository;
import de.augmentia.rag.repository.VectorSearchRepository;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

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

    public Uni<RagResponse> processQuery(RagQuery query) {
        return Uni.createFrom().item(() -> executePipeline(query));
    }

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

    public GraphSearchResult processGraphQuery(String question, int hops, int maxNodes) {
        return graphSearchService.search(question, hops, maxNodes);
    }

    @CacheResult(cacheName = "rag-retrieval")
    List<Chunk> retrieve(QueryRouter.RoutedQuery routed) {
        Set<String> seen = new LinkedHashSet<>();
        long start = System.nanoTime();

        log.debugv("retrieve: starting with {0} sub-questions: {1}",
            routed.subQuestions().size(), routed.subQuestions());

        for (String subQ : routed.subQuestions()) {
            log.debugv("retrieve: processing sub-question '{0}'", subQ);
            float[] queryVec = embeddingClient.embed(subQ);

            var denseResults = vectorStore.search(queryVec, config.retrieve().topK());
            var sparseResults = fullTextStore.search(subQ, config.retrieve().topK());

            List<String> fusedIds = rrf.fuse(denseResults, sparseResults, config.retrieve().rrfK());
            log.debugv("retrieve: sub-question '{0}' → dense={1} sparse={2} fused={3} unique={4}",
                subQ, denseResults.size(), sparseResults.size(), fusedIds.size(), seen.size() + fusedIds.size());
            seen.addAll(fusedIds);
        }

        List<Chunk> candidates = seen.stream()
            .map(id -> chunkRepo.findById(id).map(ChunkEntity::toDomain).orElse(null))
            .filter(Objects::nonNull)
            .toList();
        log.debugv("retrieve: fetched {0}/{1} candidates from DB", candidates.size(), seen.size());

        if (routed.type() == QuestionType.COMPARISON) {
            int before = candidates.size();
            candidates = deduplicateByTitle(candidates);
            log.debugv("retrieve: comparison dedup {0} → {1}", before, candidates.size());
        }

        List<Chunk> result = reranker.rerank(routed.original(), candidates, config.rerank().topN());
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        log.debugv("retrieve: done in {0}ms — {1} candidates → {2} final chunks",
            elapsed, candidates.size(), result.size());

        return result;
    }

    String generateCitedAnswer(String question, List<Chunk> context) {
        StringBuilder contextBlock = new StringBuilder();
        for (Chunk c : context) {
            contextBlock.append(String.format("[ID: %s] %s%n%n", c.id(), c.contextualText()));
        }
        return normalizeCitations(generator.generate(contextBlock.toString(), question));
    }

    static String normalizeCitations(String answer) {
        return answer.replace('\u3010', '[').replace('\u3011', ']');
    }

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

    private String reformulateQuery(String original, VerificationResult failed) {
        return original + " (reformulated: " + String.join("; ", failed.unverifiedClaims()) + ")";
    }

    private double computeConfidence(List<Chunk> context) {
        if (context.isEmpty()) return 0.0;
        return Math.min(1.0, context.size() / (double) config.rerank().topN());
    }

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