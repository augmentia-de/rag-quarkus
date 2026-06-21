package de.augmentia.rag.repository;

import de.augmentia.rag.domain.GraphEdge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "graph_edges", indexes = {
    @Index(name = "idx_graph_edges_source", columnList = "source_node_id"),
    @Index(name = "idx_graph_edges_target", columnList = "target_node_id"),
    @Index(name = "idx_graph_edges_relation", columnList = "relation_type")
})
public class GraphEdgeEntity {

    @Id
    @Column(length = 128)
    public String id;

    @Column(name = "source_node_id", length = 128, nullable = false)
    public String sourceNodeId;

    @Column(name = "target_node_id", length = 128, nullable = false)
    public String targetNodeId;

    @Column(name = "relation_type", length = 256, nullable = false)
    public String relationType;

    @Column
    public Float weight;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public GraphEdgeEntity() {}

    public GraphEdgeEntity(String id, String sourceNodeId, String targetNodeId,
                           String relationType, Float weight, String description) {
        this.id = id;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.relationType = relationType;
        this.weight = weight;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public static GraphEdgeEntity fromDomain(GraphEdge edge) {
        return new GraphEdgeEntity(
            edge.id(), edge.sourceNodeId(), edge.targetNodeId(),
            edge.relationType(), edge.weight(), edge.description()
        );
    }

    public GraphEdge toDomain() {
        return new GraphEdge(id, sourceNodeId, targetNodeId, relationType, weight, description, createdAt);
    }
}
