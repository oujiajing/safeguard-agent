package com.safeguard.agent.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * SSE confirm 事件载荷，前端据此展示确认卡片，messageId 用于后续确认请求
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentConfirmPayload(String messageId, String title, List<AgentConfirmCall> calls) {
}
