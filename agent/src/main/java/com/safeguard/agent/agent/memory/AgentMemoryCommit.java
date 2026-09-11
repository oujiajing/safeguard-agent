package com.safeguard.agent.agent.memory;

import com.safeguard.agent.agent.enums.AgentMemorySourceType;

import java.util.List;

/**
 * 一次提交的全部入参：快照期取到的凭证连同 Judge 决策一起交给提交侧复核
 * merges 只在容量顶到上限那次非空，与决策在同一事务里落地
 */
public record AgentMemoryCommit(String userId,
                                String conversationId,
                                String extractionId,
                                int attemptCount,
                                long expectedRevision,
                                String expectedWatermark,
                                AgentMemorySourceType sourceType,
                                List<AgentMemoryDecision> decisions,
                                List<AgentMemoryMerge> merges) {
}
