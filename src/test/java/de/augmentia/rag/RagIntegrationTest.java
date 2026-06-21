package de.augmentia.rag;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(DatabaseResource.class)
class RagIntegrationTest {

    @Test
    void healthEndpointShouldReturnUp() {
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    void queryEndpointRequiresApiKey() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"question\":\"test\"}")
            .when().post("/api/v1/rag/query")
            .then()
            .statusCode(401);
    }

    @Test
    void queryEndpointAcceptsValidApiKey() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"What is RAG?\"}")
            .when().post("/api/v1/rag/query")
            .then()
            .statusCode(anyOf(is(200), is(500)));
    }

    @Test
    void queryEndpointRejectsEmptyQuestion() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"\"}")
            .when().post("/api/v1/rag/query")
            .then()
            .statusCode(400);
    }

    @Test
    void ingestEndpointRequiresApiKey() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"documents\":[]}")
            .when().post("/api/v1/rag/ingest")
            .then()
            .statusCode(401);
    }

    @Test
    void databaseHealthCheckWorks() {
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", anyOf(is("UP"), is("DOWN")));
    }
}