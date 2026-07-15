package de.augmentia.rag.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * LLM service that scores faithfulness of a claim against context.
 *
 * <p>Returns a numeric string in [0.0, 1.0] where 1.0 = fully supported,
 * 0.0 = contradicts or not mentioned. Fault tolerant: 15s timeout, 1 retry, circuit breaker.
 */
@Singleton
@RegisterAiService
public interface JudgeAiService {

    @SystemMessage("You are a strict faithfulness grader.")
    @UserMessage("""
        You are a strict fact-checker. Decide whether the CONTEXT supports the CLAIM.

        CONTEXT:
        {context}

        CLAIM: {claim}

        Output ONLY a number: 1.0 if the context clearly states or entails the claim,
        0.0 if it contradicts or does not mention it, or a value in between.
        """)
    @Timeout(15000)
    @Retry(maxRetries = 1, delay = 500)
    @CircuitBreaker(requestVolumeThreshold = 20, failureRatio = 0.5, delay = 5000)
    String score(@V("context") String context, @V("claim") String claim);
}