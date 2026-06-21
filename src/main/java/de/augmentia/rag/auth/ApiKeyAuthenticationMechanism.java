package de.augmentia.rag.auth;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;

@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyAuthenticationMechanism implements ContainerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Inject
    @ConfigProperty(name = "rag.auth.api-key", defaultValue = "dev-key-change-me")
    String expectedApiKey;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        if (path.equals("/q/health") || path.equals("/q/openapi") ||
            path.startsWith("/q/swagger") || path.startsWith("/q/metrics")) {
            return;
        }

        String apiKey = requestContext.getHeaderString(API_KEY_HEADER);
        if (apiKey == null || !apiKey.equals(expectedApiKey)) {
            requestContext.abortWith(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Missing or invalid API key. Provide it via the X-API-Key header.\"}")
                    .build()
            );
        }
    }
}