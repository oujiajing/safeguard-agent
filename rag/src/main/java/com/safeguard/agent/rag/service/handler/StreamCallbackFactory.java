package com.safeguard.agent.rag.service.handler;

import com.safeguard.agent.framework.web.StreamTaskManager;
import com.safeguard.agent.infra.chat.StreamCallback;
import com.safeguard.agent.infra.config.AIModelProperties;
import com.safeguard.agent.rag.core.memory.ConversationMemoryService;
import com.safeguard.agent.rag.service.ConversationGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * StreamCallback 工厂
 * 负责创建各种类型的 StreamCallback 实例
 */
@Component
@RequiredArgsConstructor
public class StreamCallbackFactory {

    private final AIModelProperties modelProperties;
    private final ConversationMemoryService memoryService;
    private final ConversationGroupService conversationGroupService;
    private final StreamTaskManager taskManager;

    /**
     * 创建聊天事件处理器
     *
     * @param emitter        SSE 发射器
     * @param conversationId 会话ID
     * @param taskId         任务ID
     * @return StreamCallback 实例
     */
    public StreamCallback createChatEventHandler(SseEmitter emitter,
                                                 String conversationId,
                                                 String taskId) {
        StreamChatHandlerParams params = StreamChatHandlerParams.builder()
                .emitter(emitter)
                .conversationId(conversationId)
                .taskId(taskId)
                .modelProperties(modelProperties)
                .memoryService(memoryService)
                .conversationGroupService(conversationGroupService)
                .taskManager(taskManager)
                .build();

        return new StreamChatEventHandler(params);
    }
}
