package com.safeguard.agent.rag.core.mcp;

import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP 参数提取器接口
 */
public interface McpParameterExtractor {

    /**
     * 从用户问题中提取 MCP 工具所需的参数
     *
     * @param userQuestion 用户原始问题
     * @param tool         MCP 工具定义（McpSchema.Tool）
     * @return 提取结局（含三态：可调用 / 需澄清 / 提取失败）
     */
    McpExtractionResult extractParameters(String userQuestion, Tool tool);

    /**
     * 从用户问题中提取 MCP 工具所需的参数（支持自定义提示词）
     *
     * @param userQuestion         用户原始问题
     * @param tool                 MCP 工具定义（McpSchema.Tool）
     * @param customPromptTemplate 自定义参数提取提示词模板（可选，为空则使用默认提示词）
     * @return 提取结局（含三态：可调用 / 需澄清 / 提取失败）
     */
    default McpExtractionResult extractParameters(String userQuestion, Tool tool, String customPromptTemplate) {
        return extractParameters(userQuestion, tool);
    }
}
