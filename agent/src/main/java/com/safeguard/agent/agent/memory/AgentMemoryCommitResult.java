package com.safeguard.agent.agent.memory;

import com.safeguard.agent.agent.enums.AgentMemoryExtractionStatus;

/**
 * 提交结果；applied 是落库的决策条数，mutated 是记忆集整体有没有变（含合并/淘汰）
 */
public record AgentMemoryCommitResult(AgentMemoryExtractionStatus status, int applied, boolean mutated) {
}
