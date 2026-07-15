package de.augmentia.rag;

import de.augmentia.rag.ai.GraphExtractor;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;

@ApplicationScoped
@Alternative
@Priority(1)
@IfBuildProfile("test")
public class MockGraphExtractor implements GraphExtractor {
    @Override
    public String extract(String text) {
        return "[{\"src\":\"TechCorp\",\"rel\":\"FOUNDED_BY\",\"tgt\":\"Alice Smith\",\"desc\":\"Alice Smith founded TechCorp\"}]";
    }
}
