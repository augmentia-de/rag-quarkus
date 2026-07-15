package de.augmentia.rag;

import de.augmentia.rag.ai.EmbeddingModelClient;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;

import java.util.List;

@ApplicationScoped
@Alternative
@Priority(1)
@IfBuildProfile("test")
public class MockEmbeddingModelClient extends EmbeddingModelClient {
    @Override
    public float[] embed(String text) {
        float[] vec = new float[1024];
        for (int i = 0; i < 1024; i++) vec[i] = 0.1f;
        return vec;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(t -> {
            float[] vec = new float[1024];
            for (int i = 0; i < 1024; i++) vec[i] = 0.1f;
            return vec;
        }).toList();
    }
}
