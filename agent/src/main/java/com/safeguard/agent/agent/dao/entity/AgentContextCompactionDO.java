package com.safeguard.agent.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 上下文压缩事件（追加日志），用于事后回溯第几代摘要开始丢信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_agent_context_compaction")
public class AgentContextCompactionDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String userId;

    private String conversationId;

    /**
     * 同一会话内的第几代摘要，从 1 起
     */
    private Integer generation;

    /**
     * 本代摘要正文，下一代覆盖后这里是唯一存档
     */
    private String summary;

    private Integer materialMsgCount;

    private Integer materialChars;

    private Integer summaryChars;

    private Integer contextCharsBefore;

    private Integer contextCharsAfter;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
