package com.safeguard.agent.rag.config;

import cn.hutool.core.util.StrUtil;

/**
 * 执行架构档位，由 safeguard.engine.type 指定
 * <p>
 * 属部署级决策（切换需重启，且 AGENT 依赖外部 ReAct 服务存活），因此不开放后台切换
 */
public enum OrchestrationMode {

    /**
     * v1 编排管线：意图分类 → 检索 → 合成，链路确定、延迟低
     */
    WORKFLOW,

    /**
     * v2 ReAct 架构：主 Agent 决策，RAG 管线降级为其中一个 Tool
     */
    AGENT;

    /**
     * 解析配置值，大小写不敏感，无法识别时回落 WORKFLOW
     */
    public static OrchestrationMode of(String value) {
        if (StrUtil.isBlank(value)) {
            return WORKFLOW;
        }
        for (OrchestrationMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return WORKFLOW;
    }
}
