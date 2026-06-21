package de.augmentia.rag.engine;

import de.augmentia.rag.domain.QuestionType;
import de.augmentia.rag.domain.RagQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryRouterTest {

    private final QueryRouter router = new QueryRouter();

    @Test
    void simpleQuestionReturnsSimpleType() {
        var routed = router.route(RagQuery.of("What is the capital of France?"));
        assertEquals(QuestionType.SIMPLE, routed.type());
        assertEquals(1, routed.subQuestions().size());
    }

    @Test
    void comparisonQuestionReturnsComparisonType() {
        var routed = router.route(RagQuery.of("Compare A and B"));
        assertEquals(QuestionType.COMPARISON, routed.type());
    }

    @Test
    void comparisonWithVsReturnsComparisonType() {
        var routed = router.route(RagQuery.of("Python vs Java"));
        assertEquals(QuestionType.COMPARISON, routed.type());
    }

    @Test
    void multiHopWithBothReturnsMultiHop() {
        var routed = router.route(RagQuery.of("Both A and B are related?"));
        assertEquals(QuestionType.MULTI_HOP, routed.type());
    }

    @Test
    void multiHopWithAndAlsoReturnsMultiHop() {
        var routed = router.route(RagQuery.of("A and also B?"));
        assertEquals(QuestionType.MULTI_HOP, routed.type());
    }

    @Test
    void falsePremiseDetected() {
        var routed = router.route(RagQuery.of("When did humans stop using fire?"));
        assertEquals(QuestionType.FALSE_PREMISE, routed.type());
    }

    @Test
    void falsePremiseDetectedWithInvisible() {
        var routed = router.route(RagQuery.of("Why is the emperor invisible?"));
        assertEquals(QuestionType.FALSE_PREMISE, routed.type());
    }

    @Test
    void falsePremiseAtlantis() {
        var routed = router.route(RagQuery.of("What is the capital of Atlantis?"));
        assertEquals(QuestionType.FALSE_PREMISE, routed.type());
    }

    @Test
    void simpleQuestionPreservesOriginalText() {
        var routed = router.route(RagQuery.of("Original question text?"));
        assertEquals("Original question text?", routed.original());
    }

    @Test
    void whatAboutDecomposesToWhatIs() {
        var routed = router.route(RagQuery.of("What about quantum computing?"));
        assertEquals(QuestionType.MULTI_HOP, routed.type());
        assertEquals("what is quantum computing?", routed.subQuestions().getFirst().toLowerCase());
    }
}