package com.safeguard.agent.agent.tool;

import com.safeguard.agent.knowledge.service.KnowledgeDocumentService;
import com.safeguard.agent.knowledge.service.KnowledgeDocumentStats;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 知识库文档数量统计工具：只读聚合，不返回文档正文或敏感来源信息。
 */
public class KnowledgeDocumentStatsTool implements AgentTool {

    public static final String TOOL_NAME = "count_knowledge_documents";
    public static final String DISPLAY_NAME = "知识库文档统计";

    private final KnowledgeDocumentService knowledgeDocumentService;

    public KnowledgeDocumentStatsTool(KnowledgeDocumentService knowledgeDocumentService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "统计知识库文档数量，明确返回总文档数、启用文档数、法规文档数和已删除记录数。总文档数、启用文档数和法规文档数不包含已删除记录。";
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of(),
                "additionalProperties", false);
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> {
            KnowledgeDocumentStats stats = knowledgeDocumentService.stats();
            String text = String.format(
                    "知识库文档统计：总文档数 %d 份；启用文档数 %d 份；法规文档数 %d 份；已删除记录 %d 条。统计口径：前三项不包含已删除记录，已删除记录单独统计。",
                    stats.totalDocuments(), stats.enabledDocuments(), stats.legalDocuments(), stats.deletedDocuments());
            String toolCallId = param == null || param.getToolUseBlock() == null
                    ? null : param.getToolUseBlock().getId();
            return ToolResultBlock.builder()
                    .id(toolCallId)
                    .name(TOOL_NAME)
                    .output(TextBlock.builder().text(text).build())
                    .state(ToolResultState.SUCCESS)
                    .build();
        });
    }
}
