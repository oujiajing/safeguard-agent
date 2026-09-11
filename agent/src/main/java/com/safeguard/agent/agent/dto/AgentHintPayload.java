package com.safeguard.agent.agent.dto;

/**
 * hint 事件载荷：code 标识提示来源（AGENT_HINT / MAX_ITERATIONS），text 为展示文案
 */
public record AgentHintPayload(String code, String text) {
}
