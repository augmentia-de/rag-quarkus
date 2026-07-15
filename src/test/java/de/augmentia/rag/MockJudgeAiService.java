package de.augmentia.rag;

import de.augmentia.rag.ai.JudgeAiService;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;

@ApplicationScoped
@Alternative
@Priority(1)
@IfBuildProfile("test")
public class MockJudgeAiService implements JudgeAiService {
    @Override
    public String score(String context, String claim) {
        return "1.0";
    }
}
