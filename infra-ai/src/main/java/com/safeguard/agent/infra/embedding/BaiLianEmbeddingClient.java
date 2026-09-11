package com.safeguard.agent.infra.embedding;

import com.safeguard.agent.infra.enums.ModelProvider;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;

@Service
public class BaiLianEmbeddingClient extends AbstractOpenAIStyleEmbeddingClient {

    public BaiLianEmbeddingClient(OkHttpClient syncHttpClient) {
        super(syncHttpClient);
    }

    @Override
    public String provider() {
        return ModelProvider.BAI_LIAN.getId();
    }

    /**
     * 百炼 compatible-mode 的批量上限是 10，比其它家的 32 小得多
     * 超限不是慢而是整批 400，摄取长文档必然踩到
     */
    @Override
    protected int maxBatchSize() {
        return 10;
    }
}
