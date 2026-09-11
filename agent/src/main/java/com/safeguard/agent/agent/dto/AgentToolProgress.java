package com.safeguard.agent.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SSE tool 事件载荷，status 为 start/end，result 和 ok 仅 end 时携带
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentToolProgress(String toolCallId, String name, String displayName, String status,
                                String result, Boolean ok) {
}
