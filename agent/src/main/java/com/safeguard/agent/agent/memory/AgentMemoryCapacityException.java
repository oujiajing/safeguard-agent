package com.safeguard.agent.agent.memory;

import lombok.Getter;

/**
 * 合并与淘汰都用尽仍装不下，抛它回滚事务；专用类型避免与链路上的 IllegalStateException 混淆
 */
@Getter
class AgentMemoryCapacityException extends RuntimeException {

    private final int projectedChars;

    private final int maxChars;

    AgentMemoryCapacityException(String extractionId, int projectedChars, int maxChars) {
        super("长期记忆容量装不下, extractionId: " + extractionId
                + ", 预演字符: " + projectedChars + ", 上限: " + maxChars);
        this.projectedChars = projectedChars;
        this.maxChars = maxChars;
    }
}
