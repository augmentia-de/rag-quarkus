package de.augmentia.rag.domain;

import java.util.List;

public record VerificationResult(
    boolean isFaithful,
    List<String> unverifiedClaims,
    List<Double> claimScores
) {}