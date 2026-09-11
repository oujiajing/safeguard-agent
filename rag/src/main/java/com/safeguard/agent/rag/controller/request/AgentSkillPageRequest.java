package com.safeguard.agent.rag.controller.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 技能分页查询请求
 */
@Data
public class AgentSkillPageRequest extends Page {

    /**
     * 关键词，匹配标识、名称与适用场景
     */
    private String keyword;
}
