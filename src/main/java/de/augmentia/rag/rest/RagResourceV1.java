package de.augmentia.rag.rest;

import de.augmentia.rag.domain.*;
import de.augmentia.rag.engine.DefensivelyGroundedRagEngine;
import de.augmentia.rag.ingestion.IngestionPipeline;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Path("/api/v1/rag")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "RAG Engine", description = "Retrieval-Augmented Generation API")
public class RagResourceV1 {

    @Inject
    DefensivelyGroundedRagEngine ragEngine;

    @Inject
    IngestionPipeline ingestionPipeline;

    private static final org.jboss.logging.Logger log = org.jboss.logging.Logger.getLogger(RagResourceV1.class);

    @POST
    @Path("/retrieve")
    @Operation(summary = "Retrieve top-K chunks for a query",
               description = "Returns the top-K retrieved chunks without generating an answer")
    @APIResponse(responseCode = "200", description = "Retrieved chunks")
    @APIResponse(responseCode = "400", description = "Invalid input")
    @APIResponse(responseCode = "401", description = "Missing or invalid API key")
    public Response retrieve(@Valid @RequestBody(required = true) RagRetrieveRequest request) {
        log.debugv("retrieve: query='{0}' topK={1}", request.query(), request.topK());
        try {
            var chunks = ragEngine.retrieve(request.query(), request.topK());
            log.debugv("retrieve: returned {0} chunks for query='{1}'", chunks.size(), request.query());
            return Response.ok(chunks).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(new RagError("Retrieval error: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/graph-query")
    @Operation(summary = "GraphRAG query — traversiert Wissensgraph",
               description = "Nutzt offline-generierten Knowledge Graph für Beziehungs-Queries")
    @APIResponse(responseCode = "200", description = "Graph-basierte Ergebnisse")
    @APIResponse(responseCode = "400", description = "Invalid input")
    public Response graphQuery(@Valid @RequestBody(required = true) GraphQueryRequest request) {
        log.debugv("graphQuery: question='{0}' hops={1} maxNodes={2}",
            request.question(), request.hops(), request.maxNodes());
        try {
            GraphSearchResult result = ragEngine.processGraphQuery(
                request.question(), request.hops(), request.maxNodes());
            log.debugv("graphQuery: {0} nodes, {1} edges, {2} chunks for question='{3}'",
                result.nodes().size(), result.edges().size(), result.contextChunks().size(),
                request.question());
            return Response.ok(new GraphQueryResponse(
                result.nodes(), result.edges(), result.contextChunks())).build();
        } catch (Exception e) {
            log.error("GraphRAG query error", e);
            return Response.serverError()
                .entity(new RagError("GraphRAG error: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/fetch-url")
    @Operation(summary = "Fetch a URL and return its text content")
    @APIResponse(responseCode = "200", description = "Fetched text content")
    public Response fetchUrl(@Valid @RequestBody(required = true) RagFetchUrlRequest request) {
        try {
            var client = HttpClient.newHttpClient();
            var req = HttpRequest.newBuilder(URI.create(request.url()))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("User-Agent", "rag-quarkus/1.0")
                .GET().build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return Response.serverError()
                    .entity(new RagError("URL returned status " + res.statusCode()))
                    .build();
            }
            return Response.ok(new RagFetchUrlResponse(res.body())).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(new RagError("Fetch error: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/query")
    @Blocking
    @Operation(summary = "Answer a question with cited evidence",
               description = "Runs the full 4-layer RAG pipeline: retrieve, constrain, verify, abstain")
    @APIResponse(responseCode = "200", description = "Generated answer with citations")
    @APIResponse(responseCode = "400", description = "Invalid input")
    @APIResponse(responseCode = "401", description = "Missing or invalid API key")
    public Uni<Response> query(@Valid @RequestBody(required = true) RagQueryRequest request) {
        log.debugv("query: question='{0}'", request.question());
        var query = new RagQuery(request.question(), null, 20, true);
        return ragEngine.processQuery(query)
            .map(response -> {
                log.debugv("query: completed for question='{0}'", request.question());
                return Response.ok(response).build();
            })
            .onFailure().recoverWithItem(e -> {
                log.error("RAG pipeline error", e);
                return Response.serverError()
                    .entity(new RagError("RAG pipeline error: " + e.getMessage()))
                    .build();
            });
    }

    @POST
    @Path("/ingest")
    @Operation(summary = "Ingest documents into the index",
               description = "Cleans, chunks, contextualizes, embeds, and indexes documents")
    @APIResponse(responseCode = "200", description = "Ingestion result")
    public Response ingest(@Valid @RequestBody(required = true) IngestRequest request) {
        log.debugv("ingest: {0} documents received", request.documents().size());
        try {
            var chunks = request.documents().stream()
                .map(d -> new Chunk(d.id(), d.docId(), d.title(), d.text(), d.text(), List.of()))
                .toList();
            var result = ingestionPipeline.ingest(chunks);
            log.debugv("ingest: {0} input passages, {1} indexed, {2} duplicates removed",
                result.inputPassages(), result.chunksIndexed(), result.duplicatesRemoved());
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(new RagError("Ingestion error: " + e.getMessage()))
                .build();
        }
    }

    public record RagQueryRequest(
        @NotBlank @Size(min = 1, max = 2000) String question
    ) {}

    public record RagRetrieveRequest(
        @NotBlank @Size(min = 1, max = 2000) String query,
        @Min(1) Integer topK
    ) {
        public RagRetrieveRequest {
            if (topK == null) topK = 5;
        }
    }

    public record RagFetchUrlRequest(@NotBlank String url) {}
    public record RagFetchUrlResponse(String content) {}

    public record GraphQueryRequest(
        @NotBlank @Size(min = 1, max = 1000) String question,
        @Min(1) Integer hops,
        @Min(1) Integer maxNodes
    ) {
        public GraphQueryRequest {
            if (hops == null) hops = 2;
            if (maxNodes == null) maxNodes = 20;
        }
    }

    public record GraphQueryResponse(
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        List<Chunk> contextChunks
    ) {}

    public record IngestRequest(
        @Valid List<DocumentRequest> documents
    ) {}

    public record DocumentRequest(
        @NotBlank String id,
        @NotBlank String docId,
        @NotBlank String title,
        @NotBlank String text
    ) {}

    public record RagError(String error) {}
}