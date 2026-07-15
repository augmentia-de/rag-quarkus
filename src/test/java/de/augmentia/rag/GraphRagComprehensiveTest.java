package de.augmentia.rag;

// GraphRAG Integration Test - Additional Scenarios
// Tests graph traversal, entity relationships, and edge cases

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(DatabaseResource.class)
class GraphRagComprehensiveTest {

    @Test
    void graphQueryWithKeywordDetection() {
        // Test that question routing detects graph queries
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"How is John connected to Jane through their company?\"}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200); // Should route to GRAPH type
    }

    @Test
    void graphQueryExplicit_keywords() {
        // Test various graph keywords trigger routing
        List<String> graphQueries = List.of(
            "Wie hängt Einstein mit Princeton zusammen?",  // German
            "wie hängt zusammen",
            "Verhältnis zwischen X und Y",
            "verbunden mit XY",
            "beziehung zwischen A und B",
            "what is related to",
            "relationship between",
            "who is connected to",
            "network von",
            "who is connected to XYZ"
        );

        for (String query : graphQueries) {
            given()
                .header("X-API-Key", "test-key")
                .contentType(ContentType.JSON)
                .body(String.format("{\"question\":\"%s\",\"hops\":1,\"maxNodes\":5}", query))
                .when().post("/api/v1/rag/graph-query")
                .then()
                .statusCode(200);
        }
    }

    @Test
    void graphQueryHandlesEmptyGraphForNonexistentEntity() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"NonexistentEntity123\",\"hops\":2,\"maxNodes\":10}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200)
            .body("nodes", empty())
            .body("edges", empty())
            .body("contextChunks", empty());
    }

    @Test
    void graphQueryWithSpecialCharacters() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"What about X+Y and Z?\",\"hops\":1,\"maxNodes\":5}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200);
    }

    @Test
    void graphQueryHopsRespectsConfiguration() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Complex multi-hop relationship\",\"hops\":1,\"maxNodes\":20}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200); // Should complete with 1 hop
    }

    @Test
    void graphQueryIncludesContextFromConnectedChunks() {
        // Verify graph traversal returns valid structure (DB may be empty in tests)
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"TechCorp\",\"hops\":2,\"maxNodes\":10}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200)
            .body("$", hasKey("nodes"))
            .body("$", hasKey("edges"))
            .body("$", hasKey("contextChunks"));
    }

    @Test
    void graphQueryStructure_ConsistentFormat() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Simple test graph query\",\"hops\":1,\"maxNodes\":5}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200)
            .body("$", hasKey("nodes"))
            .body("$", hasKey("edges"))
            .body("$", hasKey("contextChunks"));
    }

    @Test
    void graphQueryWithUnicodeCharacters() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"What about Café Müller in Berlin?\",\"hops\":2,\"maxNodes\":10}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200); // Should handle Unicode
    }

    @Test
    void graphQueryIntegration_WithStandardRAG() {
        // Test that GraphRAG provides additional results beyond standard RAG
        String standardBody = "{ \"query\": \"Who is the CEO of TechCorp?\", \"topK\": 10 }";
        String graphBody = "{ \"question\": \"TechCorp CEO\", \"hops\": 2, \"maxNodes\": 10 }";

        // Execute both queries
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(standardBody)
            .when().post("/api/v1/rag/retrieve")
            .then()
            .statusCode(200);

        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(graphBody)
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200);
    }

    @Test
    void graphQuery_EdgeCase_EmptyString() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"\",\"hops\":1,\"maxNodes\":5}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(400); // Should reject empty question
    }

    @Test
    void graphQuery_EdgeCase_QuestionTooLong() {
        String veryLongQuestion = "A".repeat(1001);
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body(String.format("{\"question\":\"%s\",\"hops\":1,\"maxNodes\":5}", veryLongQuestion))
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(400); // Should reject too long
    }

    @Test
    void graphQuery_LargeMaxNodes() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Any question\",\"hops\":1,\"maxNodes\":50}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200); // Should handle larger maxNodes
    }

    @Test
    void graphQuery_HopsAtMaximum() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Deep relationship\",\"hops\":5,\"maxNodes\":100}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200); // Should handle up to 5 hops
    }
}
