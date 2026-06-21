package de.augmentia.rag.engine;

import de.augmentia.rag.repository.SearchResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class ReciprocalRankFusion {

    private static final Logger log = Logger.getLogger(ReciprocalRankFusion.class);

    static final int DEFAULT_K = 60;

    public List<String> fuse(List<SearchResult> denseResults, List<SearchResult> sparseResults) {
        return fuse(denseResults, sparseResults, DEFAULT_K);
    }

    public List<String> fuse(List<SearchResult> denseResults, List<SearchResult> sparseResults, int k) {
        Map<String, Double> scores = new HashMap<>();

        for (int rank = 0; rank < denseResults.size(); rank++) {
            String id = denseResults.get(rank).getChunkId();
            scores.merge(id, 1.0 / (k + rank + 1), Double::sum);
        }
        for (int rank = 0; rank < sparseResults.size(); rank++) {
            String id = sparseResults.get(rank).getChunkId();
            scores.merge(id, 1.0 / (k + rank + 1), Double::sum);
        }

        var sorted = scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .toList();

        log.debugv("RRF: dense={0} sparse={1} fused={2} k={3}",
            denseResults.size(), sparseResults.size(), sorted.size(), k);
        if (!sorted.isEmpty()) {
            log.debugv("RRF: top3={0}",
                sorted.stream().limit(3).collect(Collectors.joining(",")));
        }

        return sorted;
    }
}