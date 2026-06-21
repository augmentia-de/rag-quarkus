package de.augmentia.rag.ingestion;

import de.augmentia.rag.domain.Chunk;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.*;

@ApplicationScoped
public class NearDuplicateDeduper {

    private static final Logger log = Logger.getLogger(NearDuplicateDeduper.class);
    private static final double THRESHOLD = 0.9;

    public DedupResult deduplicate(List<Chunk> chunks) {
        log.debugv("deduper: deduplicating {0} chunks", chunks.size());
        List<Chunk> kept = new ArrayList<>();
        List<MinHash> signatures = new ArrayList<>();

        for (Chunk chunk : chunks) {
            MinHash mh = MinHash.of(chunk.text());
            boolean isDuplicate = false;

            for (MinHash existing : signatures) {
                double jaccard = mh.jaccardIndex(existing);
                if (jaccard >= THRESHOLD) {
                    log.debugv("deduper: chunk '{0}' is duplicate (jaccard={1})", chunk.id(), String.format("%.3f", jaccard));
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                signatures.add(mh);
                kept.add(chunk);
            }
        }

        log.debugv("deduper: kept {0}/{1} chunks (dropped {2})", kept.size(), chunks.size(), chunks.size() - kept.size());
        return new DedupResult(kept, chunks.size() - kept.size());
    }

    public record DedupResult(List<Chunk> kept, int droppedCount) {}

    private record MinHash(int[] hashValues) {
        private static final int NUM_PERM = 64;

        static MinHash of(String text) {
            Set<String> shingles = shingle(text);
            int[] hashValues = new int[NUM_PERM];
            Random rng = new Random(42);

            for (int i = 0; i < NUM_PERM; i++) {
                final int seed = rng.nextInt();
                hashValues[i] = shingles.stream()
                    .mapToInt(s -> (s.hashCode() ^ seed) & 0x7FFFFFFF)
                    .min()
                    .orElse(0);
            }
            return new MinHash(hashValues);
        }

        double jaccardIndex(MinHash other) {
            int matches = 0;
            for (int i = 0; i < NUM_PERM; i++) {
                if (this.hashValues[i] == other.hashValues[i]) {
                    matches++;
                }
            }
            return (double) matches / NUM_PERM;
        }

        private static Set<String> shingle(String text) {
            Set<String> shingles = new HashSet<>();
            String cleaned = text.replaceAll("\\s+", " ").toLowerCase();
            for (int i = 0; i <= cleaned.length() - 3; i++) {
                shingles.add(cleaned.substring(i, i + 3));
            }
            return shingles;
        }
    }
}