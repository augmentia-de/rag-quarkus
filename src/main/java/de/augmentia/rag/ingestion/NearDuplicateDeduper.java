package de.augmentia.rag.ingestion;

import de.augmentia.rag.domain.Chunk;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.*;

@ApplicationScoped
public class NearDuplicateDeduper {

    private static final Logger log = Logger.getLogger(NearDuplicateDeduper.class);
    private static final double THRESHOLD = 0.9;
    private static final int NUM_PERM = 64;
    private static final int BANDS = 16;
    private static final int ROWS_PER_BAND = 4;
    private static final int[] PERMUTATION_SEEDS;

    static {
        Random rng = new Random(42);
        PERMUTATION_SEEDS = new int[NUM_PERM];
        for (int i = 0; i < NUM_PERM; i++) {
            PERMUTATION_SEEDS[i] = rng.nextInt();
        }
    }

    public DedupResult deduplicate(List<Chunk> chunks) {
        log.debugv("deduper: deduplicating {0} chunks", chunks.size());
        List<Chunk> kept = new ArrayList<>();
        List<int[]> signatures = new ArrayList<>();

        for (Chunk chunk : chunks) {
            int[] sig = computeMinHash(shingle(chunk.text()));
            boolean isDuplicate = false;

            for (int[] existing : signatures) {
                double jaccard = computeJaccard(sig, existing);
                if (jaccard >= THRESHOLD) {
                    log.debugv("deduper: chunk '{0}' is duplicate (jaccard={1})",
                        chunk.id(), String.format("%.3f", jaccard));
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                signatures.add(sig);
                kept.add(chunk);
            }
        }

        log.debugv("deduper: kept {0}/{1} chunks (dropped {2})",
            kept.size(), chunks.size(), chunks.size() - kept.size());
        return new DedupResult(kept, chunks.size() - kept.size());
    }

    private List<String> shingle(String text) {
        if (text == null || text.isBlank()) return List.of();
        String cleaned = text.toLowerCase().replaceAll("\\s+", " ");
        List<String> shingles = new ArrayList<>();
        for (int i = 0; i <= cleaned.length() - 3; i++) {
            shingles.add(cleaned.substring(i, i + 3));
        }
        return shingles;
    }

    private int[] computeMinHash(List<String> shingles) {
        int[] signature = new int[NUM_PERM];
        Arrays.fill(signature, Integer.MAX_VALUE);

        if (shingles.isEmpty()) return signature;

        for (int i = 0; i < NUM_PERM; i++) {
            int seed = PERMUTATION_SEEDS[i];
            for (String shingle : shingles) {
                int hash = (shingle.hashCode() ^ seed) & 0x7FFFFFFF;
                if (hash < signature[i]) {
                    signature[i] = hash;
                }
            }
        }
        return signature;
    }

    private double computeJaccard(int[] sig1, int[] sig2) {
        int matches = 0;
        for (int i = 0; i < NUM_PERM; i++) {
            if (sig1[i] == sig2[i]) matches++;
        }
        return (double) matches / NUM_PERM;
    }

    public record DedupResult(List<Chunk> kept, int droppedCount) {}
}
