package de.augmentia.rag.engine;

import de.augmentia.rag.domain.AtomicClaim;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ClaimDecomposer {

    private static final Logger log = Logger.getLogger(ClaimDecomposer.class);

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");
    private static final Pattern SENTENCE_PATTERN =
        Pattern.compile("\\s*([^.!?]+[.!?](?:\\s*\\[[^\\]]+\\])*)");

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

            String chunkId = citationMatcher.group(1).trim()
                .replaceFirst("(?i)^ID:\\s*", "");
            String statement = citationMatcher.replaceAll("").replaceAll("\\s+", " ").trim();
            if (!statement.isBlank()) {
                claims.add(new AtomicClaim(statement, chunkId));
            }
        }

        log.debugv("claimDecomposer: {0} sentences → {1} claims", sentences.size(), claims.size());
        return claims;
    }
}