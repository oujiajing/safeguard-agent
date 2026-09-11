package com.safeguard.agent.agent.dto;

/**
 * meta 事件载荷：会话与任务标识
 */
public record AgentMetaPayload(String conversationId, String taskId) {
}
