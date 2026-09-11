package com.safeguard.agent.agent.controller.vo;

import com.safeguard.agent.agent.dto.AgentBlock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Agent 消息视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentMessageVO {

    /**
     * 消息ID
     */
    private String id;

    /**
     * user / assistant
     */
    private String role;

    /**
     * 消息正文
     */
    private String content;

    /**
     * 思考内容
     */
    private String thinkingContent;

    /**
     * 运行轨迹块，回放还原时间线；旧数据为 null 时由前端按 content/thinking 合成
     */
    private List<AgentBlock> blocks;

    /**
     * NORMAL / INTERRUPTED
     */
    private String messageStatus;

    /**
     * 创建时间
     */
    private Date createTime;
}
