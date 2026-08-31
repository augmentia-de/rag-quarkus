package de.augmentia.rag;

import de.augmentia.rag.ai.FusedChunkAnalysisService;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@ApplicationScoped
@Alternative
@Priority(1)
@IfBuildProfile("test")
public class MockFusedChunkAnalysisService implements FusedChunkAnalysisService {
    @Override
    public String analyze(String title, String docText, String chunkText) {
        return """
            {"contextPrefix": "This chunk provides context about the document structure and main topics.",
             "triples": [{"src":"TechCorp","rel":"PART_OF","tgt":"AI Industry","desc":"TechCorp operates in the AI industry"}]}
            """;
    }
}