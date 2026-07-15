package de.augmentia.rag.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class ChunkRepository implements PanacheRepository<ChunkEntity> {

    public Optional<ChunkEntity> findById(String id) {
        return find("id", id).firstResultOptional();
    }

    public long countByDocId(String docId) {
        return count("docId", docId);
    }

    public long countNotGraphExtractedByDocIds(java.util.Collection<String> docIds) {
        return count("docId IN ?1 AND graphExtracted = false", java.util.List.copyOf(docIds));
    }

    public java.util.List<ChunkEntity> findByDocIdsNotGraphExtracted(java.util.Collection<String> docIds) {
        return find("docId IN ?1 AND graphExtracted = false", java.util.List.copyOf(docIds)).list();
    }
}