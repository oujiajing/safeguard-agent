package com.safeguard.agent.agent.controller.request;

/**
 * 确认请求入参
 *
 * @param conversationId 会话ID
 * @param messageId      消息ID
 * @param approved       是否批准
 */
public record ConfirmRequest(String conversationId, String messageId, boolean approved) {
}
