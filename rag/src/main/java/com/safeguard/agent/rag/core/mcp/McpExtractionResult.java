package com.safeguard.agent.rag.core.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP 参数提取结局
 * <p>
 * 区分三态，供消费端决定是否调用工具：
 * - {@link Status#SUCCESS}：参数已就绪，可调用工具
 * - {@link Status#NEED_CLARIFICATION}：缺少必填参数（用户未提供），不调用工具、需向用户追问，missingRequired 列出缺失项
 * - {@link Status#FAILED}：无法提取到有效参数（协议畸形 / 值非法），不调用工具
 *
 * @param status          提取结局
 * @param params          已提取的有效参数（SUCCESS 用于调用；其余态仅作记录）
 * @param missingRequired 用户未提供的必填参数名（仅 NEED_CLARIFICATION 非空）
 */
public record McpExtractionResult(Status status, Map<String, Object> params, List<String> missingRequired) {

    public enum Status {
        SUCCESS,
        NEED_CLARIFICATION,
        FAILED
    }

    public static McpExtractionResult success(Map<String, Object> params) {
        return new McpExtractionResult(Status.SUCCESS, params, List.of());
    }

    public static McpExtractionResult needClarification(Map<String, Object> params, List<String> missingRequired) {
        return new McpExtractionResult(Status.NEED_CLARIFICATION, params, List.copyOf(missingRequired));
    }

    public static McpExtractionResult failed() {
        return new McpExtractionResult(Status.FAILED, Map.of(), List.of());
    }
}
