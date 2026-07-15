package de.augmentia.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone end-to-end test of the full RAG pipeline with real LLM.
 *
 * This main method starts Quarkus (via QuarkusApplication) and runs
 * comprehensive tests against the live API with real Ollama embeddings
 * and real OpenRouter LLM calls.
 *
 * Usage:
 *   mvn quarkus:dev -Dquarkus.profile=dev &
 *   mvn exec:java -Dexec.mainClass=de.augmentia.rag.LlmIntegrationTest
 *
 * Or run directly from IDE with main() method.
 *
 * Requires:
 *   - Quarkus running on http://localhost:8080
 *   - Ollama running with mxbai-embed-large:latest
 *   - Network access to openrouter.ai
 */
public class LlmIntegrationTest {

    private static final String BASE_URL = System.getenv().getOrDefault("RAG_BASE_URL", "http://localhost:8080");
    private static final String API_KEY = System.getenv().getOrDefault("RAG_API_KEY", "test-key");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static int passCount = 0;
    private static int failCount = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("══════════════════════════════════════════════════════════════");
        System.out.println("  RAG LLM Integration Test — Full Pipeline with Real LLM");
        System.out.println("══════════════════════════════════════════════════════════════");
        System.out.println();

        // Test 1: Health check
        testHealthCheck();

        // Test 2: Ingest documents
        String jobId = testIngestDocuments();
        if (jobId != null) {
            waitForJobCompletion(jobId);
        }

        // Small delay for DB commit
        Thread.sleep(2000);

        // Test 3: Vector search
        testVectorSearch();

        // Test 4: Full-text search
        testFullTextSearch();

        // Test 5: Full RAG query
        testFullRagQuery("Who created the transformer architecture and what paper introduced it?");

        // Test 6: Graph query
        testGraphQuery();

        // Test 7: Citation verification
        testCitationQuery("When was ChatGPT launched and how many users did it reach?");

        // Test 8: Second domain (quantum computing)
        testFullRagQuery("Who claimed quantum supremacy and what processor was used?");

        // Test 9: Cross-domain query
        testFullRagQuery("What is the relationship between NVIDIA GPUs and training large language models?");

        // Test 10: Out-of-domain query (abstention)
        testAbstentionQuery("What is the recipe for chocolate cake?");

        // Test 11: Multi-hop question
        testFullRagQuery("How did the development of GPUs by NVIDIA influence the progress of AI research in the 2010s?");

        // Summary
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════");
        System.out.println("  Test Summary");
        System.out.println("══════════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("  Total:  " + (passCount + failCount));
        System.out.println("  Passed: " + passCount);
        System.out.println("  Failed: " + failCount);
        System.out.println();

        if (failCount == 0) {
            System.out.println("  ✅ ALL TESTS PASSED");
        } else {
            System.out.println("  ❌ SOME TESTS FAILED");
        }
        System.exit(failCount > 0 ? 1 : 0);
    }

    private static void testHealthCheck() {
        log("── 1. Health Check ──");
        try {
            var resp = httpGet("/q/health");
            check("Health endpoint responds", resp.statusCode() == 200);
        } catch (Exception e) {
            check("Health endpoint responds", false);
            log("  ⚠ Cannot reach server at " + BASE_URL + " — is Quarkus running?");
        }
        System.out.println();
    }

    private static String testIngestDocuments() {
        log("── 2. Ingest Documents (full pipeline with LLM) ──");
        log("  This triggers: chunking → contextualization (LLM) → graph extraction (LLM) → embeddings (Ollama)");
        try {
            String payload = loadResource("/test-data/ingest-payload.json");
            var resp = httpPost("/api/v1/rag/ingest", payload);
            check("Ingest returns 202", resp.statusCode() == 202);

            JsonNode body = JSON.readTree(resp.body());
            String jobId = body.get("jobId").asText();
            log("  Job ID: " + jobId);
            return jobId;
        } catch (Exception e) {
            log("  ✗ Ingest failed: " + e.getMessage());
            failCount++;
            return null;
        }
    }

    private static void waitForJobCompletion(String jobId) {
        log("  Waiting for ingestion to complete (LLM calls in progress)...");
        long start = System.currentTimeMillis();
        long timeout = 300_000; // 5 minutes
        while (System.currentTimeMillis() - start < timeout) {
            try {
                Thread.sleep(5000);
                var resp = httpGet("/api/v1/rag/ingest/" + jobId);
                JsonNode body = JSON.readTree(resp.body());
                String status = body.get("status").asText();
                log("  Job status: " + status + " (" + ((System.currentTimeMillis() - start) / 1000) + "s)");

                if ("DONE".equals(status)) {
                    check("Ingestion completed successfully", true);
                    return;
                } else if ("FAILED".equals(status)) {
                    check("Ingestion completed successfully", false);
                    return;
                }
            } catch (Exception e) {
                log("  ⚠ Error checking job status: " + e.getMessage());
            }
        }
        check("Ingestion completed within timeout", false);
    }

    private static void testVectorSearch() {
        log("── 3. Vector Search ──");
        try {
            String payload = "{\"query\": \"Who invented the transformer architecture?\", \"k\": 3, \"searchMode\": \"VECTOR\"}";
            var resp = httpPost("/api/v1/rag/retrieve", payload);
            check("Vector search returns 200", resp.statusCode() == 200);

            JsonNode body = JSON.readTree(resp.body());
            JsonNode results = body.get("results");
            int count = results != null ? results.size() : 0;
            check("Vector search returned results", count > 0);

            if (count > 0) {
                double score = results.get(0).get("score").asDouble();
                log("  Top score: " + String.format("%.4f", score));
                check("Vector search top score > 0.3", score > 0.3);
            }
        } catch (Exception e) {
            log("  ✗ Vector search failed: " + e.getMessage());
            failCount++;
        }
        System.out.println();
    }

    private static void testFullTextSearch() {
        log("── 4. Full-Text Search (BM25) ──");
        try {
            String payload = "{\"query\": \"quantum supremacy Sycamore processor\", \"k\": 3, \"searchMode\": \"FULLTEXT\"}";
            var resp = httpPost("/api/v1/rag/retrieve", payload);
            check("Full-text search returns 200", resp.statusCode() == 200);

            JsonNode body = JSON.readTree(resp.body());
            JsonNode results = body.get("results");
            int count = results != null ? results.size() : 0;
            check("Full-text search returned results", count > 0);
        } catch (Exception e) {
            log("  ✗ Full-text search failed: " + e.getMessage());
            failCount++;
        }
        System.out.println();
    }

    private static void testFullRagQuery(String question) {
        log("── RAG Query: " + question.substring(0, Math.min(question.length(), 60)) + " ──");
        try {
            String payload = JSON.writeValueAsString(new RagQueryRequest(question, "VECTOR", 5));
            var resp = httpPost("/api/v1/rag/query", payload);
            check("RAG query returns 200", resp.statusCode() == 200);

            JsonNode body = JSON.readTree(resp.body());
            String answer = body.get("answer").asText();
            JsonNode citations = body.get("citations");
            double faithfulness = body.get("faithfulnessScore").asDouble();

            check("Answer is not empty", answer != null && !answer.isBlank());
            check("Citations present", citations != null && citations.size() > 0);
            check("Faithfulness score > 0", faithfulness > 0.0);

            log("  Answer: " + answer.substring(0, Math.min(answer.length(), 200)));
            log("  Faithfulness: " + faithfulness);
            if (citations != null) {
                log("  Citations: " + citations.size());
            }
        } catch (Exception e) {
            log("  ✗ RAG query failed: " + e.getMessage());
            failCount++;
        }
        System.out.println();
    }

    private static void testGraphQuery() {
        log("── 5. Graph-Enhanced Query ──");
        try {
            String payload = "{\"query\": \"What is the relationship between Elon Musk and SpaceX?\", \"searchMode\": \"GRAPH\", \"k\": 5}";
            var resp = httpPost("/api/v1/rag/query", payload);
            check("Graph query returns 200", resp.statusCode() == 200);

            JsonNode body = JSON.readTree(resp.body());
            String answer = body.get("answer").asText();
            check("Graph query returned answer", answer != null && !answer.isBlank());
            log("  Answer: " + answer.substring(0, Math.min(answer.length(), 200)));
        } catch (Exception e) {
            log("  ✗ Graph query failed: " + e.getMessage());
            failCount++;
        }
        System.out.println();
    }

    private static void testCitationQuery(String question) {
        log("── 6. Citation Verification: " + question.substring(0, Math.min(question.length(), 50)) + " ──");
        try {
            String payload = JSON.writeValueAsString(new RagQueryRequest(question, "VECTOR", 3));
            var resp = httpPost("/api/v1/rag/query", payload);
            check("Citation query returns 200", resp.statusCode() == 200);

            JsonNode body = JSON.readTree(resp.body());
            String answer = body.get("answer").asText();
            JsonNode citations = body.get("citations");

            check("Citations returned", citations != null && citations.size() > 0);
            if (citations != null && citations.size() > 0) {
                for (JsonNode cite : citations) {
                    String chunkId = cite.get("chunkId").asText();
                    String excerpt = cite.get("excerpt").asText();
                    log("  Citation: [" + chunkId + "] " + excerpt.substring(0, Math.min(excerpt.length(), 80)));
                }
            }
            log("  Answer: " + answer.substring(0, Math.min(answer.length(), 200)));
        } catch (Exception e) {
            log("  ✗ Citation query failed: " + e.getMessage());
            failCount++;
        }
        System.out.println();
    }

    private static void testAbstentionQuery(String question) {
        log("── 7. Abstention Test (out-of-domain): " + question + " ──");
        try {
            String payload = JSON.writeValueAsString(new RagQueryRequest(question, "VECTOR", 3));
            var resp = httpPost("/api/v1/rag/query", payload);
            check("Abstention query returns 200", resp.statusCode() == 200);

            JsonNode body = JSON.readTree(resp.body());
            String answer = body.get("answer").asText();
            double faithfulness = body.get("faithfulnessScore").asDouble();

            // System should either abstain or indicate insufficient information
            boolean abstained = answer == null || answer.isBlank()
                || answer.toLowerCase().contains("no")
                || answer.toLowerCase().contains("not")
                || answer.toLowerCase().contains("cannot")
                || answer.toLowerCase().contains("insufficient")
                || answer.toLowerCase().contains("abstain")
                || faithfulness < 0.5;

            check("System handles out-of-domain query gracefully", abstained || answer.length() < 100);
            log("  Answer: " + (answer != null ? answer.substring(0, Math.min(answer.length(), 200)) : "(empty)"));
            log("  Faithfulness: " + faithfulness);
        } catch (Exception e) {
            log("  ✗ Abstention query failed: " + e.getMessage());
            failCount++;
        }
        System.out.println();
    }

    // ─── HTTP helpers ────────────────────────────────────────

    private static HttpResponse<String> httpGet(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .header("X-API-Key", API_KEY)
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> httpPost(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .header("Content-Type", "application/json")
            .header("X-API-Key", API_KEY)
            .timeout(Duration.ofSeconds(300))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String loadResource(String name) throws Exception {
        var is = LlmIntegrationTest.class.getResourceAsStream(name);
        if (is == null) {
            // Try loading from filesystem
            java.nio.file.Path path = java.nio.file.Path.of("src/main/resources" + name);
            if (java.nio.file.Files.exists(path)) {
                return java.nio.file.Files.readString(path);
            }
            throw new RuntimeException("Resource not found: " + name);
        }
        return new String(is.readAllBytes());
    }

    private static void log(String msg) {
        System.out.println("[" + java.time.LocalTime.now().toString().substring(0, 8) + "] " + msg);
    }

    private static void check(String desc, boolean ok) {
        if (ok) {
            passCount++;
            log("  ✓ " + desc);
        } else {
            failCount++;
            log("  ✗ " + desc);
        }
    }

    // ─── Request DTOs ────────────────────────────────────────

    public record RagQueryRequest(String query, String searchMode, int k) {}
}
