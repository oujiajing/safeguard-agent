package com.safeguard.agent.rag.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 某智能体的槽位配置，槽位元数据与生效判定一并下发，前端不再维护第二份映射表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentPromptConfigVO {

    private String agentId;

    private String agentName;

    private Boolean builtin;

    /**
     * 内置智能体的名称，控制台文案要指名道姓说清空后沿用谁
     */
    private String defaultAgentName;

    /**
     * 当前执行架构，取自 safeguard.engine.type，仅供展示
     */
    private String mode;

    private List<Slot> slots;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Slot {

        private String slotKey;
        private String displayName;

        /**
         * 编辑器上方的写法提醒，普通提示词无需额外解释故为 null
         */
        private String editorHint;

        /**
         * 控制台分栏：WORKFLOW / AGENT / COMMON
         */
        private String group;

        private String groupName;

        /**
         * 该槽位在当前架构下是否生效
         */
        private Boolean effective;

        /**
         * 未生效原因，生效时为 null
         */
        private String inactiveReason;

        private Set<String> requiredPlaceholders;

        /**
         * 本智能体自身配置的内容，空白表示未配置并回落内置
         */
        private String content;
    }
}
