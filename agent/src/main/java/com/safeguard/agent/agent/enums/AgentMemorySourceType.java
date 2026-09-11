package com.safeguard.agent.agent.enums;

/**
 * 长期记忆条目的写入来源，语义上只有容量淘汰按它分档（FLUSH 殿后）
 */
public enum AgentMemorySourceType {

    /**
     * 模型在对话里调 flush_memory 触发
     */
    FLUSH,

    /**
     * 轮次释放后的后台抽取
     */
    BACKGROUND,

    /**
     * 容量反压时的合并产物
     */
    CONSOLIDATION
}
