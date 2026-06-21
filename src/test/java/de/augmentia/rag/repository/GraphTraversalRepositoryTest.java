package de.augmentia.rag.repository;

import de.augmentia.rag.DatabaseResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(DatabaseResource.class)
class GraphTraversalRepositoryTest {

    @Inject GraphTraversalRepository repository;
    @Inject EntityManager em;

    @BeforeEach
    @Transactional
    void setupGraphStructure() {
        em.createNativeQuery("DELETE FROM graph_edges").executeUpdate();
        em.createNativeQuery("DELETE FROM graph_nodes").executeUpdate();

        em.createNativeQuery("INSERT INTO graph_nodes (id, entity_name, entity_type) VALUES ('n1', 'Alpha Corp', 'COMPANY')").executeUpdate();
        em.createNativeQuery("INSERT INTO graph_nodes (id, entity_name, entity_type) VALUES ('n2', 'CEO John', 'PERSON')").executeUpdate();
        em.createNativeQuery("INSERT INTO graph_nodes (id, entity_name, entity_type) VALUES ('n3', 'Beta LLC', 'COMPANY')").executeUpdate();

        em.createNativeQuery("INSERT INTO graph_edges (id, source_node_id, target_node_id, relation_type) VALUES ('e1', 'n2', 'n1', 'MANAGES')").executeUpdate();
        em.createNativeQuery("INSERT INTO graph_edges (id, source_node_id, target_node_id, relation_type) VALUES ('e2', 'n1', 'n3', 'PARTNER')").executeUpdate();
    }

    @Test
    void testEquiJoinBfsTraversal() {
        List<String> hop1 = repository.findConnectedNodeIds(List.of("n2"), 1, 10);
        assertTrue(hop1.contains("n2"));
        assertTrue(hop1.contains("n1"));
        assertFalse(hop1.contains("n3"));

        List<String> hop2 = repository.findConnectedNodeIds(List.of("n2"), 2, 10);
        assertTrue(hop2.contains("n3"));
    }
}
