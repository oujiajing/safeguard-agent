package com.safeguard.agent.rag.core.retrieval.postprocessor;

import com.safeguard.agent.framework.convention.RetrievedChunk;
import com.safeguard.agent.framework.convention.RetrievedChunkKey;
import com.safeguard.agent.rag.core.retrieval.channel.SearchChannelResult;
import com.safeguard.agent.rag.core.retrieval.channel.SearchContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 去重后置处理器
 * <p>
 * 合并多个通道的结果并按 key 去重，同一 Chunk 多路命中时保留首次出现的实例
 * 不比较跨通道原始分数，最终名次由下游 RRF 融合赋分
 */
@Component
public class DeduplicationPostProcessor implements SearchResultPostProcessor {

    @Override
    public String getName() {
        return "Deduplication";
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return true;
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        Map<String, RetrievedChunk> chunkMap = new LinkedHashMap<>();
        for (SearchChannelResult result : results) {
            for (RetrievedChunk chunk : result.getChunks()) {
                chunkMap.putIfAbsent(RetrievedChunkKey.of(chunk), chunk);
            }
        }
        return new ArrayList<>(chunkMap.values());
    }
}
