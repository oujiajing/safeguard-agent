package com.safeguard.agent.agent.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 流式对话服务
 */
public interface AgentChatService {

    /**
     * 发起 Agent 流式对话
     */
    void streamChat(String question, String conversationId, SseEmitter emitter);

    /**
     * 裁决挂起的写操作并续跑：同意则执行工具，拒绝则让模型带着「用户已取消」继续作答
     * 参数只带同意与否，待执行的工具与入参一律从 Agent 状态里取原件
     */
    void confirmPendingTool(String conversationId, String messageId, boolean approved, SseEmitter emitter);

    /**
     * 停止指定任务
     */
    void stopTask(String taskId);
}
