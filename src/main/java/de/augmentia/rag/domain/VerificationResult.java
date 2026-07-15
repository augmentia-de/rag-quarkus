package de.augmentia.rag.domain;

import java.util.List;

/**
 * Result of faithfulness verification against retrieved context.
 *
 * @param isFaithful true when ALL claims pass the threshold
 * @param unverifiedClaims list of claim texts that failed verification
 * @param claimScores parallel list of scores for each claim (0.0-1.0)
 */
public record VerificationResult(
    boolean isFaithful,
    List<String> unverifiedClaims,
    List<Double> claimScores
) {}