package com.safeguard.agent.agent.controller;

import com.safeguard.agent.agent.config.AgentProperties;
import com.safeguard.agent.agent.config.ConditionalOnAgentEngine;
import com.safeguard.agent.agent.controller.request.ConfirmRequest;
import com.safeguard.agent.agent.service.AgentChatService;
import com.safeguard.agent.framework.convention.Result;
import com.safeguard.agent.framework.validation.ChatQuestion;
import com.safeguard.agent.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 对话入口，仅 safeguard.engine.type=agent 时注册；RAG v3 接口不受影响
 */
@RestController
@ConditionalOnAgentEngine
@RequiredArgsConstructor
public class AgentChatController {

    private final AgentChatService agentChatService;
    private final AgentProperties agentProperties;

    @GetMapping(value = "/agent/v1/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam @ChatQuestion String question,
                           @RequestParam(required = false) String conversationId) {
        SseEmitter emitter = new SseEmitter(agentProperties.getSseTimeoutMs());
        agentChatService.streamChat(question, conversationId, emitter);
        return emitter;
    }

    @PostMapping(value = "/agent/v1/chat/confirm", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter confirm(@RequestBody ConfirmRequest requestParam) {
        SseEmitter emitter = new SseEmitter(agentProperties.getSseTimeoutMs());
        agentChatService.confirmPendingTool(requestParam.conversationId(), requestParam.messageId(),
                requestParam.approved(), emitter);
        return emitter;
    }

    @PostMapping("/agent/v1/stop")
    public Result<Void> stop(@RequestParam String taskId) {
        agentChatService.stopTask(taskId);
        return Results.success();
    }
}
