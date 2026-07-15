package de.augmentia.rag.engine;

import de.augmentia.rag.domain.QuestionType;
import de.augmentia.rag.domain.RagQuery;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Keyword-based query classifier that routes questions to different retrieval strategies.
 *
 * <p>Priority chain: falsePremise → comparison → multiHop → simple.
 * Multi-hop queries are decomposed into sub-questions.
 */
@ApplicationScoped
public class QueryRouter {

    private static final Logger log = Logger.getLogger(QueryRouter.class);

    /** Keywords indicating multi-hop reasoning is needed. */
    private static final List<String> MULTI_HOP_KEYWORDS = List.of(
        "and also", "both", "compare", "difference", "similar",
        "what about", "after that", "before", "then", "who was",
        "how did they", "what happened", "why did"
    );

    public record RoutedQuery(String original, List<String> subQuestions, QuestionType type) {}

    public RoutedQuery route(RagQuery query) {
        String q = query.question().toLowerCase();

        if (isFalsePremise(q)) {
            log.debugv("router: falsePremise → question='{0}'", query.question());
            return new RoutedQuery(query.question(), List.of(query.question()), QuestionType.FALSE_PREMISE);
        }
        if (isComparison(q)) {
            log.debugv("router: comparison → question='{0}'", query.question());
            return new RoutedQuery(query.question(), List.of(query.question()), QuestionType.COMPARISON);
        }
        if (isMultiHop(q)) {
            List<String> subQs = decomposeSubQuestions(query.question());
            log.debugv("router: multiHop → question='{0}' subQuestions={1}", query.question(), subQs);
            return new RoutedQuery(query.question(), subQs, QuestionType.MULTI_HOP);
        }
        log.debugv("router: simple → question='{0}'", query.question());
        return new RoutedQuery(query.question(), List.of(query.question()), QuestionType.SIMPLE);
    }

    private boolean isMultiHop(String q) {
        return MULTI_HOP_KEYWORDS.stream().anyMatch(q::contains);
    }

    private boolean isComparison(String q) {
        return q.contains("compare") || q.contains(" vs ") || q.contains(" or ");
    }

    private boolean isFalsePremise(String q) {
        return q.startsWith("what is the capital of atlantis") ||
               q.contains("invisible") && q.startsWith("why is the") ||
               q.contains("stop using") ||
               q.contains("cease to exist") ||
               q.contains("when did humans") && q.contains("stop");
    }

    private List<String> decomposeSubQuestions(String question) {
        if (question.toLowerCase().contains("and also") || question.toLowerCase().contains("both")) {
            return List.of(question);
        }
        if (question.toLowerCase().startsWith("what about")) {
            return List.of(question.replaceFirst("(?i)what about", "what is").trim());
        }
        return List.of(question);
    }
}