package de.augmentia.rag.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.RequestScoped;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * LLM service that generates cited answers from retrieved context.
 *
 * <p>Contract: every sentence must end with [ID: xx] citation. If context is
 * insufficient, returns "ABSTAIN". Fault tolerant: 30s timeout, 2 retries, circuit breaker.
 */
@RequestScoped
@RegisterAiService
public interface GeneratorAiService {

    @SystemMessage("""
        You are an untrusted agent operating inside a strict containment sandbox.
        Answer the query based ONLY on the provided context blocks.

        CRITICAL INVARIANTS:
        1. Every sentence must end with a citation like [ID: xx]. Use square brackets.
        2. If the context does not completely support a statement, do not write it.
        3. If the context is insufficient to answer, output only: ABSTAIN
        """)
    @UserMessage("CONTEXT:\n{context}\n\nQUERY: {query}")
    @Timeout(30000)
    @Retry(maxRetries = 2, delay = 1000)
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10000)
    String generate(@V("context") String context, @V("query") String query);
}