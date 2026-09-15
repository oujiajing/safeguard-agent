package com.safeguard.agent.rag.core.prompt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.safeguard.agent.rag.dao.entity.AgentProfileDO;
import com.safeguard.agent.rag.dao.entity.AgentPromptDO;
import com.safeguard.agent.rag.dao.mapper.AgentProfileMapper;
import com.safeguard.agent.rag.dao.mapper.AgentPromptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体提示词解析器
 * 优先取激活智能体的槽位，空白则回落内置智能体；面向终端用户的提示词一律从此处读取
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPromptResolver {

    private static final String MANDATORY_AGENT_SAFETY_RULES = """

            # 写入安全硬规则
            - 评估结果中的 action/proposal 只是说明系统具备后续能力，不代表用户已经授权，也不是下一步指令。
            - 用户说“不要创建”“不需要下发”“无需执行”“仅评估”“只查询”等否定表达时，绝不调用任何创建、下发、提交、变更或执行工具，也不要弹出确认卡。
            - 只有用户明确提出创建、下发、提交或执行，并且必要参数已经确认时，才可以提出写操作；否则只回答评估结果或追问缺失信息。
            - 涉及施工隐患时，优先使用基于 assessmentId 的专用整改工具；创建工单只收集公司、部门、班组中文名称，由系统向 Safe-team 解析内部标识，不要向用户索要或臆造 ID。
            - 严禁调用不存在的 create_task、create_rectification_order 或 issue_rectification；创建整改工单只能调用 create_rectification_from_assessment，且必须先展示确认卡。
            """;

    private final AgentProfileMapper agentProfileMapper;
    private final AgentPromptMapper agentPromptMapper;
    private final AgentPromptCacheManager cacheManager;

    /**
     * @return 槽位提示词，内置智能体也没配时返回空串
     */
    public String resolve(AgentPromptSlot slot) {
        if (slot == null) {
            return "";
        }
        String content = StrUtil.emptyIfNull(resolveAll().get(slot.name()));
        if (slot == AgentPromptSlot.AGENT_MAIN && StrUtil.isNotBlank(content)
                && !content.contains("# 写入安全硬规则")) {
            return content + MANDATORY_AGENT_SAFETY_RULES;
        }
        return content;
    }

    /**
     * 填充占位符并清理格式，语义与 PromptTemplateLoader#render 一致
     */
    public String render(AgentPromptSlot slot, Map<String, String> slots) {
        return PromptTemplateUtils.cleanupPrompt(PromptTemplateUtils.fillSlots(resolve(slot), slots));
    }

    /**
     * @return 全部槽位的最终生效内容，缺失的槽位不出现在 map 中
     */
    public Map<String, String> resolveAll() {
        Map<String, String> cached = cacheManager.getFromCache();
        if (cached != null) {
            return cached;
        }
        Map<String, String> resolved = loadFromDb();
        cacheManager.saveToCache(resolved);
        return resolved;
    }

    /**
     * 读取某个智能体自身配置的槽位，不做回落，供控制台编辑态展示
     */
    public Map<String, String> loadOwnPrompts(String agentId) {
        Map<String, String> own = new HashMap<>();
        if (StrUtil.isBlank(agentId)) {
            return own;
        }
        List<AgentPromptDO> prompts = agentPromptMapper.selectList(
                Wrappers.lambdaQuery(AgentPromptDO.class).eq(AgentPromptDO::getAgentId, agentId));
        for (AgentPromptDO prompt : prompts) {
            own.put(prompt.getSlotKey(), StrUtil.emptyIfNull(prompt.getContent()));
        }
        return own;
    }

    private Map<String, String> loadFromDb() {
        AgentProfileDO builtin = firstByFlag(AgentProfileDO::getBuiltin);
        if (builtin == null) {
            log.warn("未找到内置智能体，空槽位将无提示词可回落");
        }

        // 先铺内置作为基线，再让激活智能体的非空槽位覆盖；两者同为一条时重复覆盖无副作用
        Map<String, String> resolved = new HashMap<>();
        putNonBlank(resolved, builtin);
        putNonBlank(resolved, firstByFlag(AgentProfileDO::getActive));
        return resolved;
    }

    private AgentProfileDO firstByFlag(SFunction<AgentProfileDO, Integer> flag) {
        List<AgentProfileDO> profiles = agentProfileMapper.selectList(
                Wrappers.lambdaQuery(AgentProfileDO.class)
                        .eq(flag, 1)
                        .orderByAsc(AgentProfileDO::getCreateTime)
                        .orderByAsc(AgentProfileDO::getId));
        return CollUtil.isEmpty(profiles) ? null : profiles.get(0);
    }

    /**
     * 后写入者覆盖前者，空白内容不参与覆盖，以此实现回落
     */
    private void putNonBlank(Map<String, String> target, AgentProfileDO profile) {
        if (profile == null) {
            return;
        }
        loadOwnPrompts(profile.getId()).forEach((slotKey, content) -> {
            if (StrUtil.isNotBlank(content)) {
                target.put(slotKey, content);
            }
        });
    }
}
