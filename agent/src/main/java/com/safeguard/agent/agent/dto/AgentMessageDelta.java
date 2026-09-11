package com.safeguard.agent.agent.dto;

/**
 * message 事件载荷：type 取 response / think
 */
public record AgentMessageDelta(String type, String delta) {
}
