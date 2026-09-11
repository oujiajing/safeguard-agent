package com.safeguard.agent.rag.core.retrieval.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeguard.agent.rag.config.SearchChannelProperties;
import com.safeguard.agent.rag.core.retrieval.RetrievalBudget;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * You.com 联网检索通道真实 API 集成测试
 * <p>
 * 仅当环境变量 YDC_API_KEY 存在时运行（1 次真实调用），日常构建自动跳过
 */
@DisplayName("You.com 联网检索通道（真实 API）")
@EnabledIfEnvironmentVariable(named = "YDC_API_KEY", matches = ".+")
class WebSearchChannelLiveTest {

    @Test
    @DisplayName("真实检索返回非空 Chunk，且带来源链接")
    void liveSearchReturnsChunks() {
        SearchChannelProperties properties = new SearchChannelProperties();
        SearchChannelProperties.WebSearch webSearch = properties.getChannels().getWebSearch();
        webSearch.setEnabled(true);
        webSearch.setCount(3);
        // api-key 留空 -> 回退环境变量 YDC_API_KEY

        WebSearchChannel channel = new WebSearchChannel(
                new OkHttpClient(), new ObjectMapper(), properties);

        assertTrue(channel.isEnabled(SearchContext.builder().budget(RetrievalBudget.uniform(3)).build()));

        SearchChannelResult result = channel.search(SearchContext.builder()
                .originalQuestion("What is Retrieval-Augmented Generation?")
                .budget(RetrievalBudget.uniform(3))
                .build());

        assertFalse(result.getChunks().isEmpty(), "真实 API 应返回至少一条结果");
        assertTrue(result.getChunks().get(0).getText().contains("来源: "), "Chunk 文本应包含来源链接");
        System.out.println("[LIVE] You.com 返回 " + result.getChunks().size() + " 个 Chunk, 耗时 "
                + result.getLatencyMs() + "ms");
        System.out.println("[LIVE] 首条 Chunk:\n" + result.getChunks().get(0).getText());
    }
}
