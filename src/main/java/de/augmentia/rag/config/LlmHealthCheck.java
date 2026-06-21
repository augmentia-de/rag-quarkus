package de.augmentia.rag.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.jboss.logging.Logger;

@Liveness
@ApplicationScoped
public class LlmHealthCheck implements HealthCheck {

    private static final Logger log = Logger.getLogger(LlmHealthCheck.class);

    @Inject
    RagConfig config;

    @Override
    public HealthCheckResponse call() {
        try {
            String endpoint = config.llm().endpoint() + "/models";
            var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(endpoint))
                .timeout(java.time.Duration.ofSeconds(5))
                .GET().build();
            var client = java.net.http.HttpClient.newHttpClient();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return HealthCheckResponse.up("llm");
            }
            return HealthCheckResponse.down("llm");
        } catch (Exception e) {
            log.warnv("LLM health check failed: {0}", e.getMessage());
            return HealthCheckResponse.down("llm");
        }
    }
}