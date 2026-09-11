package com.safeguard.agent.rag.core.skill;

import java.util.List;

/**
 * 运行期技能，供 Agent 侧读取，不含控制台才关心的审计字段
 *
 * @param skillCode   技能标识，模型按此名加载正文
 * @param name        展示名
 * @param description 适用场景，随清单交给模型判断要不要加载
 * @param content     技能正文 Markdown
 * @param toolIds     加载本技能后才解锁的 MCP 工具 ID，未加载时对模型不可见；正文按名引用其它工具不受影响
 */
public record AgentSkill(
        String skillCode,
        String name,
        String description,
        String content,
        List<String> toolIds) {

    public AgentSkill {
        toolIds = toolIds == null ? List.of() : List.copyOf(toolIds);
    }
}
