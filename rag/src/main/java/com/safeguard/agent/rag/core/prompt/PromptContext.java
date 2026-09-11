package com.safeguard.agent.rag.core.prompt;

import cn.hutool.core.util.StrUtil;
import com.safeguard.agent.rag.core.intent.NodeScore;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * Prompt 构建上下文，封装一次 RAG 请求中用于组装提示词的全部输入数据
 */
@Data
@Builder
public class PromptContext {

    /**
     * 用户原始问题
     */
    private String question;

    /**
     * MCP 工具调用返回的上下文文本（已格式化）
     */
    private String mcpContext;

    /**
     * 知识库检索返回的上下文文本（已格式化）
     */
    private String kbContext;

    /**
     * MCP 通道命中的意图及其得分列表
     */
    private List<NodeScore> mcpIntents;

    /**
     * 知识库通道命中的意图及其得分列表
     */
    private List<NodeScore> kbIntents;

    /**
     * 允许参与模板选择和规则注入的意图 ID
     */
    @Builder.Default
    private Set<String> eligibleIntentIds = Set.of();

    /**
     * 是否包含 MCP 上下文
     */
    public boolean hasMcp() {
        return StrUtil.isNotBlank(mcpContext);
    }

    /**
     * 是否包含知识库上下文
     */
    public boolean hasKb() {
        return StrUtil.isNotBlank(kbContext);
    }
}
