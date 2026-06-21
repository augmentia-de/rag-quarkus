package de.augmentia.rag.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rag_ingestion_jobs")
public class IngestionJobEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    public JobStatus status;

    @Column(name = "total_chunks")
    public int totalChunks;

    @Column(name = "processed_chunks")
    public int processedChunks;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    public enum JobStatus {
        PENDING, RUNNING, DONE, FAILED
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
