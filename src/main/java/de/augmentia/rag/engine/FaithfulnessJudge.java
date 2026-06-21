package de.augmentia.rag.engine;

import de.augmentia.rag.ai.JudgeAiService;
import de.augmentia.rag.config.RagConfig;
import de.augmentia.rag.domain.AtomicClaim;
import de.augmentia.rag.domain.Chunk;
import de.augmentia.rag.domain.VerificationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class FaithfulnessJudge {

    private static final Logger log = LoggerFactory.getLogger(FaithfulnessJudge.class);
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
    private static final Pattern SCORE_PATTERN = Pattern.compile("[01](?:\\.\\d+)?");

    @Inject
    JudgeAiService judge;

    @Inject
    RagConfig config;

    public VerificationResult verify(List<AtomicClaim> claims, List<Chunk> context) {
        String combinedContext = context.stream()
            .map(c -> "[Chunk " + c.id() + "] " + c.contextualText())
            .collect(Collectors.joining("\n\n"));

        if (claims.isEmpty()) {
            return new VerificationResult(true, List.of(), List.of());
        }

        List<CompletableFuture<ScoredClaim>> futures = claims.stream()
            .map(claim -> CompletableFuture.supplyAsync(
                () -> evaluate(claim, combinedContext),
                executor))
            .toList();

        List<String> failures = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            ScoredClaim sc = futures.get(i).join();
            scores.add(sc.score);
            if (sc.score < config.judge().tauClaim()) {
                failures.add(claims.get(i).statement());
            }
        }

        return new VerificationResult(failures.isEmpty(), failures, scores);
    }

    private ScoredClaim evaluate(AtomicClaim claim, String context) {
        if (context.isBlank()) {
            log.warn("evaluate: blank context for claim='{}' citedChunkId='{}'", claim.statement(), claim.citedChunkId());
            return new ScoredClaim(0.0);
        }
        try {
            String response = judge.score(context, claim.statement()).toLowerCase();
            var matcher = SCORE_PATTERN.matcher(response);
            double score = matcher.find() ? Double.parseDouble(matcher.group()) : 0.0;
            score = Math.min(1.0, Math.max(0.0, score));
            log.info("evaluate: claim='{}' context(prefix)='{}' response='{}' score={}", claim.statement(), context.substring(0, Math.min(80, context.length())), response, score);
            return new ScoredClaim(score);
        } catch (Exception e) {
            log.warn("evaluate: exception", e);
            return new ScoredClaim(0.0);
        }
    }

    private record ScoredClaim(double score) {}
}