package de.augmentia.rag.domain;

import java.util.List;

/**
 * RAG pipeline response: cited answer with verification results.
 *
 * @param answer generated text with [ID: xx] citations, or abstention message
 * @param abstained true when pipeline could not produce a verifiable answer
 * @param citations extracted chunk IDs referenced in the answer
 * @param verification faithfulness verification results per claim
 */
public record RagResponse(
    String answer,
    boolean abstained,
    List<String> citations,
    List<VerificationResult> verification
) {
    /** Returns a standard abstention response with no answer or citations. */
    public static RagResponse abstain() {
        return new RagResponse(
            "I do not have enough verifiable evidence to answer this question.",
            true, List.of(), List.of()
        );
    }
}