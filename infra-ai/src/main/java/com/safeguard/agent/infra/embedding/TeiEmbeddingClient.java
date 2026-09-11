package com.safeguard.agent.infra.embedding;

import com.google.gson.JsonObject;
import com.safeguard.agent.infra.enums.ModelProvider;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TeiEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    public TeiEmbeddingClient(OkHttpClient syncHttpClient) {
        super(syncHttpClient);
    }

    @Override
    public String provider() {
        return ModelProvider.TEI.getId();
    }

    @Override
    protected boolean requiresApiKey() {
        return false;
    }

    @Override
    protected int maxBatchSize() {
        return 16;
    }

    @Override
    protected void customizeRequestBody(JsonObject body, com.safeguard.agent.infra.model.ModelTarget target) {
        body.remove("dimensions");
        body.remove("encoding_format");
    }

    @Override
    protected List<List<Float>> doEmbed(List<String> texts,
                                        com.safeguard.agent.infra.model.ModelTarget target) {
        List<List<Float>> embeddings = super.doEmbed(texts, target);
        int dimension = target.candidate().getDimension();
        return embeddings.stream().map(vector -> {
            if (vector.size() == dimension) return vector;
            if (vector.size() > dimension) {
                throw new IllegalStateException("TEI 向量维度超过目标维度: " + vector.size() + ">" + dimension);
            }
            List<Float> padded = new ArrayList<>(vector);
            while (padded.size() < dimension) padded.add(0F);
            return List.copyOf(padded);
        }).toList();
    }
}
