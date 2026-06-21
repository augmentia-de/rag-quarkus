package de.augmentia.rag;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class GraphRagEndpointTest {

    @Test
    void graphQueryEndpointRequiresApiKey() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Who directed Doctor Strange?\"}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(401);
    }

    @Test
    void graphQueryEndpointRejectsEmptyQuestion() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\" \"}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(400);
    }
}
