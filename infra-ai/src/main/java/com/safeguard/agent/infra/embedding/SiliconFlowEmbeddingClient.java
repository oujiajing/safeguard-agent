package com.safeguard.agent.infra.embedding;

import com.safeguard.agent.infra.enums.ModelProvider;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

@Service
public class SiliconFlowEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    public SiliconFlowEmbeddingClient(OkHttpClient syncHttpClient) {
        super(syncHttpClient);
    }

    @Override
    public String provider() {
        return ModelProvider.SILICON_FLOW.getId();
    }

    @Override
    protected int maxBatchSize() {
        return 32;
    }
}
