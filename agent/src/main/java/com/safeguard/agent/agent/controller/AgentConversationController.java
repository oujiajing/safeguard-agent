package com.safeguard.agent.agent.controller;

import com.safeguard.agent.agent.config.ConditionalOnAgentEngine;
import com.safeguard.agent.agent.controller.vo.AgentConversationVO;
import com.safeguard.agent.agent.controller.vo.AgentMessageVO;
import com.safeguard.agent.agent.service.AgentConversationService;
import com.safeguard.agent.framework.context.UserContext;
import com.safeguard.agent.framework.convention.Result;
import com.safeguard.agent.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 会话最小 CRUD，与 workflow 会话接口两套分立
 */
@RestController
@ConditionalOnAgentEngine
@RequiredArgsConstructor
public class AgentConversationController {

    private final AgentConversationService agentConversationService;

    @GetMapping("/agent/v1/conversations")
    public Result<List<AgentConversationVO>> listConversations() {
        return Results.success(agentConversationService.listByUserId(UserContext.getUserId()));
    }

    @GetMapping("/agent/v1/conversations/{conversationId}/messages")
    public Result<List<AgentMessageVO>> listMessages(@PathVariable String conversationId) {
        return Results.success(agentConversationService.listMessages(conversationId, UserContext.getUserId()));
    }

    @PutMapping("/agent/v1/conversations/{conversationId}/title")
    public Result<Void> rename(@PathVariable String conversationId, @RequestBody TitleRequest request) {
        agentConversationService.rename(conversationId, UserContext.getUserId(),
                request == null ? null : request.title());
        return Results.success();
    }

    @DeleteMapping("/agent/v1/conversations/{conversationId}")
    public Result<Void> delete(@PathVariable String conversationId) {
        agentConversationService.delete(conversationId, UserContext.getUserId());
        return Results.success();
    }

    @PostMapping("/agent/v1/conversations/batch-delete")
    public Result<Void> batchDelete(@RequestBody BatchDeleteRequest request) {
        agentConversationService.deleteBatch(request == null ? List.of() : request.ids(), UserContext.getUserId());
        return Results.success();
    }

    public record TitleRequest(String title) {
    }

    public record BatchDeleteRequest(List<String> ids) {
    }
}
