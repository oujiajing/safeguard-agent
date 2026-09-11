package com.safeguard.agent.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.safeguard.agent.framework.convention.ChatMessage.MessageStatus;
import com.safeguard.agent.framework.convention.SourceRef;

import java.util.List;

/**
 * 模型回复完成事件载荷
 *
 * @param messageId 消息ID（字符串，避免前端精度丢失）
 * @param title     会话标题（可选）
 * @param sources   文档级来源列表（可选，仅命中知识库时携带）
 * @param messageStatus 消息结束状态
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompletionPayload(String messageId, String title, List<SourceRef> sources, MessageStatus messageStatus) {

    /**
     * 无来源场景的便捷构造 sources 置空（NON_NULL 序列化时自动省略该字段）
     */
    public CompletionPayload(String messageId, String title) {
        this(messageId, title, null, MessageStatus.NORMAL);
    }
}
