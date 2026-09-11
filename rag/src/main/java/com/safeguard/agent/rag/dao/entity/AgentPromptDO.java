package com.safeguard.agent.rag.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_agent_prompt")
public class AgentPromptDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String agentId;

    /**
     * 槽位标识，取值见 AgentPromptSlot
     */
    private String slotKey;

    /**
     * 提示词全文，空白视为未配置并回落内置智能体
     */
    private String content;

    private String createBy;
    private String updateBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer deleted;
}
