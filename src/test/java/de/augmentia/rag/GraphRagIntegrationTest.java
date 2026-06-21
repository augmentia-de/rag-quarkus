package de.augmentia.rag;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(DatabaseResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphRagIntegrationTest {

    @BeforeAll
    static void seedGraphData() {
        var pg = DatabaseResource.postgres;
        var ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setUrl(pg.getJdbcUrl());
        ds.setUser(pg.getUsername());
        ds.setPassword(pg.getPassword());

        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                INSERT INTO rag_chunks (id, doc_id, title, text, contextual_text, token_count)
                VALUES ('chunk-g1', 'test-doc', 'Test Doc',
                        'Scott Derrickson directed Doctor Strange.',
                        'Scott Derrickson directed Doctor Strange.', 10)
                ON CONFLICT (id) DO NOTHING
            """);
            stmt.execute("""
                INSERT INTO graph_nodes (id, chunk_id, entity_name, entity_type, description, embedding) VALUES
                ('n1', 'chunk-g1', 'Scott Derrickson', 'PERSON', 'American film director',
                 ARRAY(SELECT 0::float FROM generate_series(1,1024))::vector),
                ('n2', 'chunk-g1', 'Doctor Strange', 'MOVIE', 'Marvel film',
                 ARRAY(SELECT 0::float FROM generate_series(1,1024))::vector)
                ON CONFLICT (id) DO NOTHING
            """);
            stmt.execute("""
                INSERT INTO graph_edges (id, source_node_id, target_node_id, relation_type, weight, description)
                VALUES ('e1', 'n1', 'n2', 'DIRECTED', 1.0,
                        'Scott Derrickson directed the film Doctor Strange')
                ON CONFLICT (id) DO NOTHING
            """);
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed graph data", e);
        }
    }

    @Test
    @Order(1)
    void graphQueryEndpointRequiresApiKey() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Who directed Doctor Strange?\"}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(2)
    void graphQueryEndpointRejectsEmptyQuestion() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"\"}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(3)
    void graphQueryEndpointReturnsNodesEdgesAndChunks() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"Scott Derrickson\",\"hops\":2,\"maxNodes\":20}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200)
            .body("nodes", not(empty()))
            .body("edges", not(empty()))
            .body("contextChunks", not(empty()))
            .body("nodes[0].entityName", containsString("Scott"));
    }

    @Test
    @Order(4)
    void graphQueryEndpointReturnsValidStructure() {
        given()
            .header("X-API-Key", "test-key")
            .contentType(ContentType.JSON)
            .body("{\"question\":\"NonexistentEntityXYZ\",\"hops\":2,\"maxNodes\":20}")
            .when().post("/api/v1/rag/graph-query")
            .then()
            .statusCode(200)
            .body("$", hasKey("nodes"))
            .body("$", hasKey("edges"))
            .body("$", hasKey("contextChunks"));
    }
}
