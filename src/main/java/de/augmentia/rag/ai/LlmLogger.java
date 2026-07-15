package de.augmentia.rag.ai;

import de.augmentia.rag.config.RagConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * Conditional LLM call logging with request-ID tracking and truncation.
 *
 * <p>Short-circuits when logging is disabled. Each call gets a unique 8-char request ID
 * for correlation. Input/output are truncated to {@code maxContentLength} characters.
 */
@ApplicationScoped
public class LlmLogger {

    private static final Logger log = Logger.getLogger(LlmLogger.class);

    @Inject
    RagConfig config;

    public String logAndExecute(String serviceName, LlmCall call) {
        if (!config.llm().logging().enabled()) {
            return call.execute();
        }

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long start = System.nanoTime();

        try {
            String response = call.execute();
            long elapsed = (System.nanoTime() - start) / 1_000_000;

            log.infof("[%s] %s completed in %dms", requestId, serviceName, elapsed);
            log.debugf("[%s] Response: %s", requestId, truncate(response));
            return response;
        } catch (Exception e) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            log.errorf("[%s] %s failed after %dms: %s", requestId, serviceName, elapsed, e.getMessage());
            throw e;
        }
    }

    public void logRequest(String serviceName, String operation, String input) {
        if (!config.llm().logging().enabled()) {
            return;
        }
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.debugf("[%s] %s.%s request: %s", requestId, serviceName, operation, truncate(input));
    }

    private String truncate(String text) {
        if (text == null) return "null";
        int maxLength = config.llm().logging().maxContentLength();
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...(" + text.length() + " chars)";
    }

    @FunctionalInterface
    public interface LlmCall {
        String execute();
    }
}
