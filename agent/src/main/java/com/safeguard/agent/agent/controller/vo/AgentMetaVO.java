package com.safeguard.agent.agent.controller.vo;

import java.util.List;

/**
 * Agent 引擎身份视图，供前端点亮状态徽标与框架信息块，只回非敏感信息
 *
 * @param framework     执行框架标识
 * @param model         当前 Chat 模型名
 * @param maxIters      单轮 ReAct 迭代上限
 * @param capabilities  引擎能力清单
 * @param toolProvider  工具提供方（原生 + MCP 桥）
 * @param mcpConfigured MCP 注册表是否有可用工具
 */
public record AgentMetaVO(
        String framework,
        String model,
        Integer maxIters,
        List<String> capabilities,
        String toolProvider,
        boolean mcpConfigured
) {
}
