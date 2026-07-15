package de.augmentia.rag;

import de.augmentia.rag.ai.ContextualizerAiService;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;

@ApplicationScoped
@Alternative
@Priority(1)
@IfBuildProfile("test")
public class MockContextualizerAiService implements ContextualizerAiService {
    @Override
    public String contextualize(String prompt) {
        return "This chunk provides context about the document structure and main topics.";
    }
}
