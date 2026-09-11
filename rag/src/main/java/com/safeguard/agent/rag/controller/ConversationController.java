package com.safeguard.agent.rag.controller;

import com.safeguard.agent.rag.controller.request.ConversationUpdateRequest;
import com.safeguard.agent.rag.controller.vo.ConversationMessageVO;
import com.safeguard.agent.rag.controller.vo.ConversationVO;
import com.safeguard.agent.framework.context.UserContext;
import com.safeguard.agent.framework.convention.Result;
import com.safeguard.agent.framework.web.Results;
import com.safeguard.agent.rag.enums.ConversationMessageOrder;
import com.safeguard.agent.rag.service.ConversationMessageService;
import com.safeguard.agent.rag.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话控制器
 * 提供会话相关的REST API接口，包括会话列表获取、重命名、删除以及会话消息列表获取等功能
 */
@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationMessageService conversationMessageService;

    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> listConversations() {
        return Results.success(conversationService.listByUserId(UserContext.getUserId()));
    }

    /**
     * 重命名会话
     */
    @PutMapping("/conversations/{conversationId}")
    public Result<Void> rename(@PathVariable String conversationId,
                               @RequestBody ConversationUpdateRequest request) {
        conversationService.rename(conversationId, request);
        return Results.success();
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> delete(@PathVariable String conversationId) {
        conversationService.delete(conversationId);
        return Results.success();
    }

    /**
     * 获取会话消息列表
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<ConversationMessageVO>> listMessages(@PathVariable String conversationId) {
        return Results.success(conversationMessageService.listMessages(conversationId, UserContext.getUserId(), null, ConversationMessageOrder.ASC));
    }
}
