package de.augmentia.rag.domain;

import java.util.List;

public record RagResponse(
    String answer,
    boolean abstained,
    List<String> citations,
    List<VerificationResult> verification
) {
    public static RagResponse abstain() {
        return new RagResponse(
            "I do not have enough verifiable evidence to answer this question.",
            true, List.of(), List.of()
        );
    }
}