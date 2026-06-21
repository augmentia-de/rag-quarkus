package de.augmentia.rag.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.RequestScoped;
import org.eclipse.microprofile.faulttolerance.Timeout;

@RequestScoped
@RegisterAiService
public interface ContextualizerAiService {

    @SystemMessage("You write a single short sentence (<25 words) that situates a document chunk within its full document context.")
    @UserMessage("{it}")
    @Timeout(180000)
    String contextualize(String prompt);
}