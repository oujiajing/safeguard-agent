package com.safeguard.agent.rag.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 智能体视图对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentProfileVO {

    private String id;
    private String name;
    private String description;

    /**
     * 头像预设标识
     */
    private String avatar;

    /**
     * 内置智能体不可编辑不可删除
     */
    private Boolean builtin;

    private Boolean active;

    /**
     * 自身已填写、且在当前架构下会被读取的槽位数，其余槽位回落内置
     */
    private Integer effectiveSlots;

    /**
     * 已填写但当前架构读不到的槽位数，切换 safeguard.engine.type 后才会生效
     */
    private Integer inactiveSlots;

    private Date createTime;
    private Date updateTime;
}
