package com.safeguard.agent.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.safeguard.agent.framework.context.UserContext;
import com.safeguard.agent.infra.chat.StreamCallback;
import com.safeguard.agent.rag.service.ratelimit.ChatQueueLimiter;
import com.safeguard.agent.rag.service.RAGChatService;
import com.safeguard.agent.rag.service.handler.StreamCallbackFactory;
import com.safeguard.agent.framework.web.StreamTaskManager;
import com.safeguard.agent.rag.service.pipeline.StreamChatContext;
import com.safeguard.agent.rag.service.pipeline.StreamChatPipeline;
import com.safeguard.agent.rag.trace.StreamChatTraceRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG 对话服务默认实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGChatServiceImpl implements RAGChatService {

    private final StreamChatPipeline chatPipeline;
    private final ChatQueueLimiter chatQueueLimiter;
    private final StreamCallbackFactory callbackFactory;
    private final StreamChatTraceRunner traceRunner;
    private final StreamTaskManager taskManager;

    @Override
    public void streamChat(String question, String conversationId, Boolean deepThinking, SseEmitter emitter) {
        String actualConversationId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        String taskId = IdUtil.getSnowflakeNextIdStr();
        StreamCallback callback = callbackFactory.createChatEventHandler(emitter, actualConversationId, taskId);

        chatQueueLimiter.enqueue(question, actualConversationId, emitter,
                () -> traceRunner.run(question, actualConversationId, taskId, callback, traceAware -> {
                    StreamChatContext ctx = StreamChatContext.builder()
                            .question(question)
                            .conversationId(actualConversationId)
                            .taskId(taskId)
                            .deepThinking(Boolean.TRUE.equals(deepThinking))
                            .userId(UserContext.getUserId())
                            .callback(traceAware)
                            .build();
                    chatPipeline.execute(ctx);
                }));
    }

    @Override
    public void stopTask(String taskId) {
        taskManager.cancelByUser(taskId);
    }
}
