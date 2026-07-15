package de.augmentia.rag;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(DatabaseResource.class)
@Execution(ExecutionMode.SAME_THREAD)
class FullPipelineIntegrationTest {

    @Test
    void fullPipelineCleanIngestAndRetrieval() {
        String payload = """
            {
                "documents": [
                    {
                        "id": "chunk-0",
                        "docId": "doc-1",
                        "title": "TechCorp Profile",
                        "text": "TechCorp was founded by Alice Smith in 2020. The company specializes in artificial intelligence and machine learning solutions. Alice Smith previously worked at BigTech Inc before starting TechCorp. The headquarters are located in Berlin, Germany. TechCorp recently launched a new product called AI Assistant Pro which helps businesses automate their workflows."
                    },
                    {
                        "id": "chunk-1",
                        "docId": "doc-2",
                        "title": "GreenEnergy Overview",
                        "text": "GreenEnergy GmbH is a German renewable energy company based in Munich. It was established in 2015 by Bob Johnson and Carol Williams. Bob Johnson serves as CEO while Carol Williams is the CTO. The company develops solar panel technology and has deployed over 5000 installations across Europe. GreenEnergy GmbH partners with TechCorp for AI-powered energy management."
                    },
                    {
                        "id": "chunk-2",
                        "docId": "doc-3",
                        "title": "Partnership Announcement",
                        "text": "The partnership between TechCorp and GreenEnergy GmbH was announced in 2024. This collaboration aims to integrate AI solutions into renewable energy systems. Alice Smith and Bob Johnson co-authored a whitepaper on sustainable AI computing. The joint research lab is located in Berlin at the TechCorp headquarters."
                    },
                    {
                        "id": "chunk-3",
                        "docId": "doc-4",
                        "title": "DataStream AG",
                        "text": "DataStream AG is a Swiss data analytics company founded in 2018 by David Mueller. It provides real-time data processing platforms for financial institutions. DataStream AG recently acquired a Berlin-based startup called StreamLabs. The company has offices in Zurich, London, and Berlin."
                    },
                    {
                        "id": "chunk-4",
                        "docId": "doc-5",
                        "title": "Alice Smith Bio",
                        "text": "Alice Smith holds a PhD in Computer Science from MIT. She published over 30 research papers on neural networks before founding TechCorp. Her doctoral thesis on attention mechanisms is considered foundational work in the field. She regularly speaks at AI conferences worldwide."
                    }
                ]
            }
            """;

        String jobId = given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/v1/rag/ingest")
        .then()
            .statusCode(202)
            .body("jobId", notNullValue())
            .extract().path("jobId");

        assertNotNull(jobId, "Job ID should not be null");

        for (int i = 0; i < 60; i++) {
            try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            Object status = given()
                .header("X-API-Key", "test-key")
                .when().get("/api/v1/rag/ingest/" + jobId)
                .then().extract().path("status");
            if ("DONE".equals(status) || "FAILED".equals(status)) break;
        }

        Map<?, ?> job = given()
            .header("X-API-Key", "test-key")
            .when().get("/api/v1/rag/ingest/" + jobId)
            .then().extract().as(Map.class);

        assertEquals("DONE", job.get("status"), "Job should be DONE. Error: " + job.get("errorMessage"));
        assertTrue((Integer) job.get("processedChunks") > 0,
            "Should have indexed chunks, got: " + job.get("processedChunks"));
    }

    @Test
    void retrievalReturnsRelevantResults() {
        seedDocuments();

        String queryPayload = """
            {
                "query": "Who founded TechCorp?",
                "topK": 5
            }
            """;

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(queryPayload)
        .when()
            .post("/api/v1/rag/retrieve")
        .then()
            .statusCode(200)
            .body("size()", greaterThan(0));
    }

    @Test
    void graphQueryReturnsStructuredResults() {
        seedDocuments();

        String graphPayload = """
            {
                "question": "Alice Smith",
                "hops": 2,
                "maxNodes": 10
            }
            """;

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(graphPayload)
        .when()
            .post("/api/v1/rag/graph-query")
        .then()
            .statusCode(200)
            .body("$", hasKey("nodes"))
            .body("$", hasKey("edges"))
            .body("$", hasKey("contextChunks"));
    }

    @Test
    void queryEndpointReturnsAnswerWithCitations() {
        seedDocuments();

        String queryPayload = """
            {
                "question": "Who founded TechCorp and where is it located?",
                "topK": 10
            }
            """;

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(queryPayload)
        .when()
            .post("/api/v1/rag/query")
        .then()
            .statusCode(200)
            .body("answer", notNullValue())
            .body("citations", notNullValue());
    }

    private void seedDocuments() {
        String payload = """
            {
                "documents": [
                    {
                        "id": "seed-1",
                        "docId": "seed-doc",
                        "title": "TechCorp Company Profile",
                        "text": "TechCorp was founded by Alice Smith in 2020. The company specializes in artificial intelligence and machine learning solutions. Alice Smith previously worked at BigTech Inc before starting TechCorp. The headquarters are located in Berlin, Germany. TechCorp recently launched a new product called AI Assistant Pro which helps businesses automate their workflows."
                    },
                    {
                        "id": "seed-2",
                        "docId": "seed-doc",
                        "title": "GreenEnergy GmbH Overview",
                        "text": "GreenEnergy GmbH is a German renewable energy company based in Munich. It was established in 2015 by Bob Johnson and Carol Williams. Bob Johnson serves as CEO while Carol Williams is the CTO. The company develops solar panel technology and has deployed over 5000 installations across Europe. GreenEnergy GmbH partners with TechCorp for AI-powered energy management."
                    },
                    {
                        "id": "seed-3",
                        "docId": "seed-doc",
                        "title": "TechCorp GreenEnergy Partnership",
                        "text": "The partnership between TechCorp and GreenEnergy GmbH was announced in 2024. This collaboration aims to integrate AI solutions into renewable energy systems. Alice Smith and Bob Johnson co-authored a whitepaper on sustainable AI computing. The joint research lab is located in Berlin at the TechCorp headquarters."
                    }
                ]
            }
            """;

        String jobId = given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/v1/rag/ingest")
        .then()
            .statusCode(202)
            .extract().path("jobId");

        for (int i = 0; i < 60; i++) {
            try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            Object status = given()
                .header("X-API-Key", "test-key")
                .when().get("/api/v1/rag/ingest/" + jobId)
                .then().extract().path("status");
            if ("DONE".equals(status) || "FAILED".equals(status)) break;
        }

        String status = given()
            .header("X-API-Key", "test-key")
            .when().get("/api/v1/rag/ingest/" + jobId)
            .then().extract().path("status");

        if (!"DONE".equals(status)) {
            throw new AssertionError("Seeding failed with status: " + status);
        }
    }
}
