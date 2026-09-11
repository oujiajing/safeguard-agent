package com.safeguard.agent.agent.dto;

/**
 * 确认卡片结算后传递给后续执行的上下文：会话标题与原始提问消息 ID
 */
public record AgentConfirmSettlement(String title, String replyToMessageId) {
}
