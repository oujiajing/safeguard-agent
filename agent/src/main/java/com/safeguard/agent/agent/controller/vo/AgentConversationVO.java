package com.safeguard.agent.agent.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Agent 会话视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentConversationVO {

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 最后活动时间
     */
    private Date lastTime;

    /**
     * 已进行轮数（用户提问计数）
     */
    private Integer turns;
}
