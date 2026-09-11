package com.safeguard.agent.rag.core.skill;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safeguard.agent.rag.dao.entity.AgentSkillDO;
import com.safeguard.agent.rag.dao.mapper.AgentSkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 技能注册表
 * 运行期只读，Agent 侧从这里取已启用技能
 */
@Component
@RequiredArgsConstructor
public class AgentSkillRegistry {

    private final AgentSkillMapper agentSkillMapper;
    private final AgentSkillCacheManager cacheManager;

    /**
     * @return 已启用技能，按 sortOrder 升序；正文一并带出，加载时不必再查库
     */
    public List<AgentSkill> listEnabled() {
        List<AgentSkill> cached = cacheManager.getFromCache();
        if (cached != null) {
            return cached;
        }
        List<AgentSkill> skills = agentSkillMapper.selectList(
                        Wrappers.lambdaQuery(AgentSkillDO.class)
                                .eq(AgentSkillDO::getEnabled, 1)
                                .orderByAsc(AgentSkillDO::getSortOrder)
                                .orderByAsc(AgentSkillDO::getId))
                .stream()
                .map(AgentSkillRegistry::toSkill)
                .toList();
        cacheManager.saveToCache(skills);
        return skills;
    }

    /**
     * @return 指定技能，停用或不存在都返回 null
     */
    public AgentSkill findByCode(String skillCode) {
        if (StrUtil.isBlank(skillCode)) {
            return null;
        }
        String target = skillCode.trim();
        return listEnabled().stream()
                .filter(skill -> target.equals(skill.skillCode()))
                .findFirst()
                .orElse(null);
    }

    private static AgentSkill toSkill(AgentSkillDO record) {
        return new AgentSkill(record.getSkillCode(), record.getName(), record.getDescription(),
                record.getContent(), record.getToolIds());
    }
}
