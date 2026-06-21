package de.augmentia.rag.mcp;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.mutiny.Uni;

import de.augmentia.rag.domain.Chunk;
import de.augmentia.rag.domain.GraphSearchResult;
import de.augmentia.rag.domain.RagQuery;
import de.augmentia.rag.domain.RagResponse;
import de.augmentia.rag.engine.DefensivelyGroundedRagEngine;
import de.augmentia.rag.engine.GraphSearchService;
import de.augmentia.rag.ingestion.IngestionPipeline;

public class RagMcpTools {

    @Inject
    DefensivelyGroundedRagEngine engine;

    @Inject
    GraphSearchService graphSearch;

    @Inject
    IngestionPipeline ingestionPipeline;

    @Tool(description = "Durchsucht das RAG-System und beantwortet eine Frage basierend auf indizierten Dokumenten. " +
            "Liefert strukturierte Antwort mit answer, abstained, citations und verification.")
    Uni<RagResponse> rag_query(
            @ToolArg(description = "Die zu beantwortende Frage") String question) {
        return engine.processQuery(RagQuery.of(question));
    }

    @Tool(description = "Ruft relevante Text-Chunks zu einer Query ab, ohne eine Antwort zu generieren.")
    List<Chunk> rag_retrieve(
            @ToolArg(description = "Die Suchanfrage") String query,
            @ToolArg(description = "Anzahl der gewünschten Ergebnisse", defaultValue = "5") int topK) {
        return engine.retrieve(query, topK);
    }

    @Tool(description = "Durchsucht den Wissensgraph nach Entitäten und Beziehungen (GraphRAG). " +
            "Liefert Knoten (nodes), Kanten (edges) und kontextuelle Chunks (contextChunks).")
    GraphSearchResult rag_graph_query(
            @ToolArg(description = "Die Suchanfrage oder Entität") String question,
            @ToolArg(description = "Traversierungstiefe (default 2)", defaultValue = "2") int hops,
            @ToolArg(description = "Maximale Knotenanzahl (default 30)", defaultValue = "30") int maxNodes) {
        return graphSearch.search(question, hops, maxNodes);
    }

    public record DocumentInput(String id, String docId, String title, String text) {}
    public record MCPIngestResult(UUID jobId, String status) {}

    @Tool(description = "Indiziert Dokumente im RAG-System. " +
            "Erwartet eine Liste von Dokumenten mit id, docId, title und text. " +
            "Führt automatisch Cleaning, Chunking, Kontextualisierung und Embedding durch.")
    MCPIngestResult rag_ingest(
            @ToolArg(description = "Liste der zu indizierenden Dokumente") List<DocumentInput> documents) {
        List<Chunk> chunks = documents.stream()
                .map(d -> new Chunk(d.id(), d.docId(), d.title(), d.text(), null, List.of()))
                .toList();
        UUID jobId = ingestionPipeline.submitForIngestion(chunks);
        return new MCPIngestResult(jobId, "PENDING");
    }
}
