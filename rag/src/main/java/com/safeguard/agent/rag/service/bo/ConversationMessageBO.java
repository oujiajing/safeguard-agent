package com.safeguard.agent.rag.service.bo;

import com.safeguard.agent.framework.convention.GroundingChunk;
import com.safeguard.agent.framework.convention.SourceRef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对话消息业务对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMessageBO {

    /**
     * 对话ID
     */
    private String conversationId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 角色：system/user/assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 深度思考内容
     */
    private String thinkingContent;

    /**
     * 深度思考耗时（秒）
     */
    private Integer thinkingDuration;

    /**
     * 回答来源，文档级来源列表（仅 assistant 消息可能有）
     */
    private List<SourceRef> sources;

    /**
     * 推荐问题 grounding 片段（仅 assistant 消息可能有，供推荐追问生成 grounding）
     */
    private List<GroundingChunk> retrievedChunks;

    /**
     * 当前助手消息对应的用户消息 ID
     */
    private String replyToMessageId;

    /**
     * 消息结束状态：NORMAL=正常完成，INTERRUPTED=用户中断，REJECTED=限流拒绝
     */
    private String messageStatus;
}
