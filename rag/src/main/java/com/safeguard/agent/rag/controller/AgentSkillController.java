package com.safeguard.agent.rag.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.safeguard.agent.framework.convention.Result;
import com.safeguard.agent.framework.web.Results;
import com.safeguard.agent.rag.controller.request.AgentSkillPageRequest;
import com.safeguard.agent.rag.controller.request.AgentSkillSaveRequest;
import com.safeguard.agent.rag.controller.vo.AgentSkillToolOptionVO;
import com.safeguard.agent.rag.controller.vo.AgentSkillVO;
import com.safeguard.agent.rag.service.AgentSkillAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能体技能管理控制器
 */
@RestController
@RequiredArgsConstructor
public class AgentSkillController {

    private final AgentSkillAdminService agentSkillAdminService;

    /**
     * 分页查询技能，不返回正文
     */
    @GetMapping("/agent-skills")
    public Result<IPage<AgentSkillVO>> page(AgentSkillPageRequest requestParam) {
        return Results.success(agentSkillAdminService.pageQuery(requestParam));
    }

    /**
     * 可引用的工具选项，来自意图树里已启用的 MCP 节点
     */
    @GetMapping("/agent-skills/tool-options")
    public Result<List<AgentSkillToolOptionVO>> toolOptions() {
        return Results.success(agentSkillAdminService.listToolOptions());
    }

    /**
     * 查询技能详情，含正文
     */
    @GetMapping("/agent-skills/{id}")
    public Result<AgentSkillVO> detail(@PathVariable String id) {
        return Results.success(agentSkillAdminService.queryById(id));
    }

    /**
     * 创建技能
     */
    @PostMapping("/agent-skills")
    public Result<String> create(@RequestBody AgentSkillSaveRequest requestParam) {
        return Results.success(agentSkillAdminService.create(requestParam));
    }

    /**
     * 更新技能，技能标识不可改
     */
    @PutMapping("/agent-skills/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody AgentSkillSaveRequest requestParam) {
        agentSkillAdminService.update(id, requestParam);
        return Results.success();
    }

    /**
     * 删除技能，它解锁的工具若不再挂在其它启用技能下，就恢复常驻可见
     */
    @DeleteMapping("/agent-skills/{id}")
    public Result<Void> delete(@PathVariable String id) {
        agentSkillAdminService.delete(id);
        return Results.success();
    }

    /**
     * 启用或停用技能
     */
    @PostMapping("/agent-skills/{id}/enabled")
    public Result<Void> toggleEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        agentSkillAdminService.toggleEnabled(id, enabled);
        return Results.success();
    }
}
