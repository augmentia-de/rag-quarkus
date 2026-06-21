package de.augmentia.rag.rest;

import de.augmentia.rag.DatabaseResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(DatabaseResource.class)
class IngestionEndpointTest {

    @Test
    void ingestReturnsAcceptedWithJobId() {
        String payload = """
            { "documents": [ { "id": "1", "docId": "d1", "title": "T", "text": "Hello Async" } ] }
            """;

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/v1/rag/ingest")
        .then()
            .statusCode(202)
            .body("jobId", notNullValue())
            .body("message", containsString("Job accepted"));
    }

    @Test
    void getJobStatusReturnsJob() {
        String payload = """
            { "documents": [ { "id": "1", "docId": "d1", "title": "T", "text": "Hello Async" } ] }
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

        given()
            .header("X-API-Key", "test-key")
            .pathParam("jobId", jobId)
        .when()
            .get("/api/v1/rag/ingest/{jobId}")
        .then()
            .statusCode(200)
            .body("id", notNullValue())
            .body("status", notNullValue());
    }
}
