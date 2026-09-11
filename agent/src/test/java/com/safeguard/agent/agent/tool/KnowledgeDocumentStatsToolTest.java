package com.safeguard.agent.agent.tool;

import com.safeguard.agent.knowledge.service.KnowledgeDocumentService;
import com.safeguard.agent.knowledge.service.KnowledgeDocumentStats;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeDocumentStatsToolTest {

    @Test
    void returnsExplicitDocumentCountsAndIsReadOnly() {
        KnowledgeDocumentService service = mock(KnowledgeDocumentService.class);
        when(service.stats()).thenReturn(new KnowledgeDocumentStats(177, 30, 30, 147));

        KnowledgeDocumentStatsTool tool = new KnowledgeDocumentStatsTool(service);
        ToolResultBlock result = tool.callAsync(null).block();

        assertThat(tool.getName()).isEqualTo("count_knowledge_documents");
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(((TextBlock) result.getOutput().get(0)).getText())
                .contains("总文档数 177 份", "启用文档数 30 份", "法规文档数 30 份", "已删除记录 147 条");
    }
}
