package de.augmentia.rag.repository;

import de.augmentia.rag.domain.GraphNode;
import de.augmentia.rag.util.VectorUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "graph_nodes", indexes = {
    @Index(name = "idx_graph_nodes_chunk_id", columnList = "chunk_id"),
    @Index(name = "idx_graph_nodes_entity_name", columnList = "entity_name")
})
public class GraphNodeEntity {

    @Id
    @Column(length = 128)
    public String id;

    @Column(name = "chunk_id", length = 128)
    public String chunkId;

    @Column(name = "entity_name", length = 512, nullable = false)
    public String entityName;

    @Column(name = "entity_type", length = 128)
    public String entityType;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public GraphNodeEntity() {}

    public GraphNodeEntity(String id, String chunkId, String entityName,
                           String entityType, String description) {
        this.id = id;
        this.chunkId = chunkId;
        this.entityName = entityName;
        this.entityType = entityType;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public static GraphNodeEntity fromDomain(GraphNode node) {
        return new GraphNodeEntity(
            node.id(), node.chunkId(), node.entityName(),
            node.entityType(), node.description()
        );
    }

    public GraphNode toDomain() {
        return new GraphNode(id, chunkId, entityName, entityType, description, null, createdAt);
    }
}
