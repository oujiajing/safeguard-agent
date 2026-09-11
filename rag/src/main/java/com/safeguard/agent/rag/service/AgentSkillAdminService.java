package com.safeguard.agent.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.safeguard.agent.rag.controller.request.AgentSkillPageRequest;
import com.safeguard.agent.rag.controller.request.AgentSkillSaveRequest;
import com.safeguard.agent.rag.controller.vo.AgentSkillToolOptionVO;
import com.safeguard.agent.rag.controller.vo.AgentSkillVO;

import java.util.List;

/**
 * 技能管理服务
 */
public interface AgentSkillAdminService {

    /**
     * 分页查询技能，不返回正文
     */
    IPage<AgentSkillVO> pageQuery(AgentSkillPageRequest requestParam);

    /**
     * 查询技能详情，含正文
     */
    AgentSkillVO queryById(String id);

    /**
     * 创建技能
     *
     * @return 新技能 ID
     */
    String create(AgentSkillSaveRequest requestParam);

    /**
     * 更新技能，技能标识不可改
     */
    void update(String id, AgentSkillSaveRequest requestParam);

    /**
     * 删除技能，它解锁的工具若不再挂在其它启用技能下，就恢复常驻可见
     */
    void delete(String id);

    /**
     * 启用或停用技能
     */
    void toggleEnabled(String id, boolean enabled);

    /**
     * 可引用的工具选项，来自意图树里已启用的 MCP 节点
     */
    List<AgentSkillToolOptionVO> listToolOptions();
}
