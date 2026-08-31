package de.augmentia.rag.repository;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Placeholder vector search targeting a dedicated Qdrant cluster.
 *
 * <p>Active only under the {@code qdrant} build profile (see {@code pom.xml}), so the
 * default pgvector implementation is used everywhere else. The binding to the Qdrant
 * gRPC/REST {@code /collections/{name}/points/search} endpoint is added here once a
 * dedicated cluster is provisioned; until then retrieval degrades to the sparse
 * full-text path only.
 */
@IfBuildProfile("qdrant")
@ApplicationScoped
public class QdrantVectorSearchRepository implements VectorSearchRepository {

    private static final Logger log = Logger.getLogger(QdrantVectorSearchRepository.class);

    @Override
    public List<SearchResult> search(float[] queryVector, int k) {
        log.warnv("qdrantVectorSearch: NOT IMPLEMENTED — returning empty results; " +
            "bind the /collections/{name}/points/search endpoint before enabling the qdrant profile");
        return List.of();
    }
}