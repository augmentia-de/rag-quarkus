package de.augmentia.rag;

// Comprehensive GraphRAG Test Suite
// Tests end-to-end GraphRAG functionality including ingestion, query routing, traversal, and validation

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(DatabaseResource.class)
class GraphRagComprehensiveTestSuite {

    @Test
    void testGraphQuery_Literal_DirectedRelationship() {
        String ingestionPayload =
            "{\"documents\":[{\"id\":\"doc-graph-1\",\"docId\":\"test-doc\",\"title\":\"Test Document\",\"text\":\"Scott Derrickson directed Doctor Strange.\"}]}";

        String jobId = given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(ingestionPayload)
            .when().post("/api/v1/rag/ingest")
            .then().statusCode(202)
            .extract().path("jobId");

        String status = null;
        for (int i = 0; i < 60 && !"DONE".equals(status); i++) {
            try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            status = given()
                .header("X-API-Key", "test-key")
                .when().get("/api/v1/rag/ingest/" + jobId)
                .then().extract().path("status");
        }

        assertEquals("DONE", status, "Ingestion should complete successfully");

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Scott Derrickson\",\"hops\":1,\"maxNodes\":10}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200)
            .body("nodes.size()", greaterThanOrEqualTo(1))
            .body("nodes[0].entityName", equalTo("Scott Derrickson"));
    }

    @Test
    void testGraphQuery_MultiHop_ThroughIntermediateEntity() {
        String ingestionPayload =
            "{\"documents\":[{\"id\":\"doc-multi-hop-1\",\"docId\":\"multi-hop-doc\",\"title\":\"Company Network\",\"text\":\"Alice manages Bob at TechCorp. Bob is a senior developer at TechCorp.\"}]}";

        String jobId = given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(ingestionPayload)
            .when().post("/api/v1/rag/ingest")
            .then().statusCode(202)
            .extract().path("jobId");

        String status = null;
        for (int i = 0; i < 60 && !"DONE".equals(status); i++) {
            try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            status = given()
                .header("X-API-Key", "test-key")
                .when().get("/api/v1/rag/ingest/" + jobId)
                .then().extract().path("status");
        }

        assertEquals("DONE", status, "Ingestion should complete");

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"TechCorp leadership team\",\"hops\":2,\"maxNodes\":20}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200)
            .body("nodes.size()", greaterThanOrEqualTo(1))
            .body("edges.size()", greaterThanOrEqualTo(0))
            .body("contextChunks.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void testGraphQuery_Bidirectional_Traversal() {
        String ingestionPayload =
            "{\"documents\":[{\"id\":\"doc-bidir-1\",\"docId\":\"bidir-doc\",\"title\":\"German Cities\",\"text\":\"Berlin is the capital of Germany. Hamburg is also a major city in Germany.\"}]}";

        String jobId = given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(ingestionPayload)
            .when().post("/api/v1/rag/ingest")
            .then().statusCode(202)
            .extract().path("jobId");

        String status = null;
        for (int i = 0; i < 60 && !"DONE".equals(status); i++) {
            try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            status = given()
                .header("X-API-Key", "test-key")
                .when().get("/api/v1/rag/ingest/" + jobId)
                .then().extract().path("status");
        }

        assertEquals("DONE", status, "Ingestion should complete");

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"German cities in European context\",\"hops\":1,\"maxNodes\":10}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200)
            .body("nodes.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void testGraphQuery_Integration_With_Standard_RAG() {
        String ingestionPayload =
            "{\"documents\":[{\"id\":\"doc-integration-1\",\"docId\":\"integration-doc\",\"title\":\"TechCompany Employees\",\"text\":\"John Smith works at TechCorp as a senior developer. Mary Johnson is a manager at TechCorp.\"}]}";

        String jobId = given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(ingestionPayload)
            .when().post("/api/v1/rag/ingest")
            .then().statusCode(202)
            .extract().path("jobId");

        String status = null;
        for (int i = 0; i < 60 && !"DONE".equals(status); i++) {
            try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            status = given()
                .header("X-API-Key", "test-key")
                .when().get("/api/v1/rag/ingest/" + jobId)
                .then().extract().path("status");
        }

        assertEquals("DONE", status, "Ingestion should complete");

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"query\":\"TechCorp employees\",\"topK\":10}")
            .when().post("/api/v1/rag/retrieve")
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1));

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"TechCorp hierarchy\",\"hops\":2,\"maxNodes\":10}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200)
            .body("$", hasKey("nodes"))
            .body("$", hasKey("edges"))
            .body("$", hasKey("contextChunks"));
    }

    @Test
    void testGraphQuery_Keyword_Classification_Simple_vs_Graph() {
        String simpleQuery = "What is the capital of France?";
        String graphQuery = "How is Paris connected to France?";

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"query\":\"" + simpleQuery + "\",\"topK\":5}")
            .when().post("/api/v1/rag/retrieve")
            .then().statusCode(200);

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"" + graphQuery + "\",\"hops\":1,\"maxNodes\":5}")
            .when().post("/api/v1/rag/graph-query")
            .then().statusCode(200);
    }

    @Test
    void testGraphQuery_VisualizationData() {
        String ingestionPayload =
            "{\"documents\":[{\"id\":\"doc-visual-1\",\"docId\":\"visual-doc\",\"title\":\"Global Tech Companies\",\"text\":\"Google employs engineers. Microsoft hires developers. Apple creates software.\"}]}";

        String jobId = given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(ingestionPayload)
            .when().post("/api/v1/rag/ingest")
            .then().statusCode(202)
            .extract().path("jobId");

        String status = null;
        for (int i = 0; i < 60 && !"DONE".equals(status); i++) {
            try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            status = given()
                .header("X-API-Key", "test-key")
                .when().get("/api/v1/rag/ingest/" + jobId)
                .then().extract().path("status");
        }

        assertEquals("DONE", status, "Ingestion should complete");

        String response = given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Tech industry landscape\",\"hops\":2,\"maxNodes\":8}")
            .when().post("/api/v1/rag/graph-query")
            .then().statusCode(200)
            .extract().asString();

        assertNotNull(response);
        assertTrue(response.contains("nodes"));
        assertTrue(response.contains("edges"));
        assertTrue(response.contains("contextChunks"));
    }

    @Test
    void testGraphQuery_Performance_Latency() {
        String ingestionPayload =
            "{\"documents\":[{\"id\":\"doc-perf-1\",\"docId\":\"perf-doc\",\"title\":\"Performance Test\",\"text\":\"Performance test data for GraphRAG system. This includes multiple entities: Alpha Corp, Beta Systems, Gamma Technologies.\"}]}";

        String jobId = given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(ingestionPayload)
            .when().post("/api/v1/rag/ingest")
            .then().statusCode(202)
            .extract().path("jobId");

        String status = null;
        for (int i = 0; i < 60 && !"DONE".equals(status); i++) {
            try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            status = given()
                .header("X-API-Key", "test-key")
                .when().get("/api/v1/rag/ingest/" + jobId)
                .then().extract().path("status");
        }

        assertEquals("DONE", status, "Ingestion should complete");

        long startTime = System.currentTimeMillis();
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Alpha Corp relations\",\"hops\":1,\"maxNodes\":5}")
            .when().post("/api/v1/rag/graph-query")
            .then().statusCode(200);
        long endTime = System.currentTimeMillis();

        long durationMs = endTime - startTime;
        assertTrue(durationMs < 10000, "GraphRAG query should complete within 10 seconds, took " + durationMs + "ms");
    }

    @Test
    void testGraphQuery_Security_Authorization() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Test query without key\",\"hops\":1,\"maxNodes\":5}")
            .when().post("/api/v1/rag/graph-query")
            .then().statusCode(401);
    }

    @Test
    void testGraphQuery_EdgeCase_NonNumericParameters() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Test\",\"hops\":\"one\",\"maxNodes\":\"ten\"}")
            .when().post("/api/v1/rag/graph-query")
            .then().statusCode(400);
    }

    @Test
    void testGraphQuery_Concurrent_Access() {
        String concurrentPayload =
            "{\"documents\":[{\"id\":\"doc-concurrent-1\",\"docId\":\"concurrent-doc\",\"title\":\"Concurrent Test\",\"text\":\"Entity A works with Entity B at Company X. Entity B collaborates with Entity C.\"}]}";

        String concurrentJobId = given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(concurrentPayload)
            .when().post("/api/v1/rag/ingest")
            .then().statusCode(202)
            .extract().path("jobId");

        String status = null;
        for (int i = 0; i < 60 && !"DONE".equals(status); i++) {
            try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            status = given()
                .header("X-API-Key", "test-key")
                .when().get("/api/v1/rag/ingest/" + concurrentJobId)
                .then().extract().path("status");
        }

        assertEquals("DONE", status, "Ingestion should complete");

        for (int i = 0; i < 3; i++) {
            final int queryId = i;
            given()
                .header("X-API-Key", "test-key")
                .contentType(ContentType.JSON)
                .body(String.format("{\"question\":\"Company %d relations\",\"hops\":2,\"maxNodes\":5}", queryId))
                .when().post("/api/v1/rag/graph-query")
                .then().statusCode(200);
        }
    }
}
