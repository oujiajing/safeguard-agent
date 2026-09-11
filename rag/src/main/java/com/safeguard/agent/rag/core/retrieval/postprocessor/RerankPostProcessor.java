package com.safeguard.agent.rag.core.retrieval.postprocessor;

import com.safeguard.agent.framework.convention.RetrievedChunk;
import com.safeguard.agent.infra.rerank.RerankService;
import com.safeguard.agent.rag.config.RAGConfigProperties;
import com.safeguard.agent.rag.core.retrieval.channel.SearchChannelResult;
import com.safeguard.agent.rag.core.retrieval.channel.SearchChannelType;
import com.safeguard.agent.rag.core.retrieval.channel.SearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rerank 后置处理器
 * <p>
 * 使用 Rerank 模型对结果进行重排序
 * 这是最后一个处理器，输出最终的 Top-K 结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RerankPostProcessor implements SearchResultPostProcessor {

    private final RerankService rerankService;
    private final RAGConfigProperties ragConfigProperties;

    @Override
    public String getName() {
        return "Rerank";
    }

    @Override
    public int getOrder() {
        return 10;  // 最后执行
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return ragConfigProperties.getRerankEnabled();
    }

    @Override
    public List<RetrievedChunk> process(List<RetrievedChunk> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks.isEmpty()) {
            log.info("Chunk 列表为空，跳过 Rerank");
            return chunks;
        }

        List<RetrievedChunk> reranked = rerankService.rerank(
                context.getMainQuestion(),
                chunks,
                context.getBudget().contextTopK()
        );

        logScoreSpread(reranked);
        logAttribution(chunks, reranked, results);
        return reranked;
    }

    /**
     * 打本批精排分的高低两端，用于校准 {@code rag.search.evidence.min-rerank-score}
     * 不并进下方多通道归因：那段在单通道下整体早退，而闸门关掉时恰恰最需要这行
     */
    private void logScoreSpread(List<RetrievedChunk> reranked) {
        List<Float> scores = reranked.stream()
                .map(RetrievedChunk::getRerankScore)
                .filter(score -> score != null && Float.isFinite(score))
                .toList();
        if (scores.isEmpty()) {
            return;
        }
        log.info("检索归因 - 精排分布: {} 条有分, 最高 {}, 最低 {}",
                scores.size(),
                scores.stream().max(Float::compare).orElseThrow(),
                scores.stream().min(Float::compare).orElseThrow());
    }

    /** 归因日志：对比 Rerank 前后各通道的候选数。 */
    private void logAttribution(List<RetrievedChunk> before,
                                List<RetrievedChunk> after,
                                List<SearchChannelResult> results) {
        if (results == null || results.size() <= 1) {
            return;
        }
        Map<String, Set<SearchChannelType>> index = ChannelAttribution.index(results);
        log.info("检索归因 - Rerank 输入按通道: {}, 输出 top{} 按通道: {}",
                ChannelAttribution.format(ChannelAttribution.countByChannel(before, index)),
                after.size(),
                ChannelAttribution.format(ChannelAttribution.countByChannel(after, index)));

    }
}
