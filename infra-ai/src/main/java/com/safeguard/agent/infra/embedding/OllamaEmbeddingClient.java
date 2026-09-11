package com.safeguard.agent.infra.embedding;

import com.google.gson.JsonObject;
import com.safeguard.agent.infra.enums.ModelProvider;
import com.safeguard.agent.infra.model.ModelTarget;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

@Service
public class OllamaEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    public OllamaEmbeddingClient(OkHttpClient syncHttpClient) {
        super(syncHttpClient);
    }

    @Override
    public String provider() {
        return ModelProvider.OLLAMA.getId();
    }

    @Override
    protected boolean requiresApiKey() {
        return false;
    }

    @Override
    protected void customizeRequestBody(JsonObject body, ModelTarget target) {
        // Ollama 不需要 encoding_format 字段
    }
}
