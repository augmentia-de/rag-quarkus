package de.augmentia.rag.domain;

public record RagQuery(
    String question,
    String userId,
    int topK,
    boolean enableAbstention
) {
    public RagQuery { // defaults
        if (topK <= 0) topK = 20;
    }

    public static RagQuery of(String question) {
        return new RagQuery(question, null, 20, true);
    }
}