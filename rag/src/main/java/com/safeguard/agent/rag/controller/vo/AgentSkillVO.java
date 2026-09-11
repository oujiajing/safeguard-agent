package com.safeguard.agent.rag.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 技能视图对象
 * 列表页不返回 content，正文只在详情里带出
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentSkillVO {

    private String id;
    private String skillCode;
    private String name;
    private String description;

    /**
     * 技能正文，列表接口不填充
     */
    private String content;

    private List<String> toolIds;
    private Integer sortOrder;
    private Boolean enabled;
    private Date createTime;
    private Date updateTime;
}
