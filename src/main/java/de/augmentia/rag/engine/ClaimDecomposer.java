package de.augmentia.rag.engine;

import de.augmentia.rag.domain.AtomicClaim;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a cited answer into atomic claims, each associated with citation IDs.
 *
 * <p>Algorithm: split on sentence boundaries, extract [ID: xxx] citations,
 * strip citations to form clean claim statements. Sentences without citations
 * are skipped.
 */
@ApplicationScoped
public class ClaimDecomposer {

    private static final Logger log = Logger.getLogger(ClaimDecomposer.class);

    /** Matches [citation] brackets — captures content inside. */
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

    /** Splits into sentences, each optionally ending with citation brackets. */
    private static final Pattern SENTENCE_PATTERN =
        Pattern.compile("\\s*([^.!?]+[.!?](?:\\s*\\[[^\\]]+\\])*)");

    /**
     * Decomposes a cited answer into atomic claims.
     * Returns empty list for null/blank input. Skips sentences without citations.
     */
    public List<AtomicClaim> decompose(String answer) {
        if (answer == null || answer.isBlank()) {
            log.debugv("claimDecomposer: empty answer, returning 0 claims");
            return List.of();
        }

        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(answer);
        while (matcher.find()) {
            String sentence = matcher.group(1).trim();
            if (!sentence.isBlank()) {
                sentences.add(sentence);
            }
        }

        if (sentences.isEmpty()) {
            sentences.add(answer.trim());
        }

        List<AtomicClaim> claims = new ArrayList<>();
        for (String sentence : sentences) {
            var citationMatcher = CITATION_PATTERN.matcher(sentence);
            if (!citationMatcher.find()) continue;

            List<String> citedIds = new ArrayList<>();
            String raw = citationMatcher.group(1).trim();
            if (!raw.startsWith("http")) {
                citedIds.add(raw.replaceFirst("(?i)^ID:\\s*", ""));
            }
            while (citationMatcher.find()) {
                raw = citationMatcher.group(1).trim();
                if (raw.startsWith("http")) continue;
                citedIds.add(raw.replaceFirst("(?i)^ID:\\s*", ""));
            }
            String statement = CITATION_PATTERN.matcher(sentence).replaceAll("").replaceAll("\\s+", " ").trim();
            if (!statement.isBlank()) {
                claims.add(new AtomicClaim(statement, citedIds.get(0), citedIds));
            }
        }

        log.debugv("claimDecomposer: {0} sentences → {1} claims", sentences.size(), claims.size());
        return claims;
    }
}