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
 * 长期记忆条目，纯追加表：失效走 invalidAt 不删行，审计链完整
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_agent_memory")
public class AgentMemoryDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String userId;

    /**
     * 自足陈述句，脱离上下文也读得懂
     */
    private String content;

    /**
     * 见 AgentMemorySourceType
     */
    private String sourceType;

    /**
     * 为空即 ACTIVE，注入与仲裁都只看这一列
     */
    private Date invalidAt;

    /**
     * 取代者ID；撤回的行留空
     */
    private String supersededBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
