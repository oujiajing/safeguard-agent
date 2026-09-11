package com.safeguard.agent.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * finish / cancel 事件载荷，Agent 模式无来源无角标
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentCompletionPayload(String messageId, String title, String messageStatus) {
}
