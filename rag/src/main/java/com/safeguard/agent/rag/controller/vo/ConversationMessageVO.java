package com.safeguard.agent.rag.controller.vo;

import com.safeguard.agent.framework.convention.SourceRef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 会话消息视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMessageVO {

    /**
     * 消息ID
     */
    private String id;

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 角色 (如: user, assistant)
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
     * 反馈值：1=点赞，-1=点踩，null=未反馈
     */
    private Integer vote;

    /**
     * 回答来源，文档级来源列表（仅 assistant 消息可能有）
     */
    private List<SourceRef> sources;

    /**
     * 推荐追问问题，答案后懒加载生成（仅 assistant 消息可能有）
     */
    private List<String> recommendedQuestions;

    /**
     * 消息结束状态：NORMAL=正常完成，INTERRUPTED=用户中断，REJECTED=限流拒绝
     */
    private String messageStatus;

    /**
     * 创建时间
     */
    private Date createTime;
}
