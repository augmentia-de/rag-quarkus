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
}