package com.safeguard.agent.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 待确认的工具调用，展示工具名和参数供用户确认
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentConfirmCall {

    /**
     * 工具调用 ID，与模型上下文中的 tool_use id 一致
     */
    private String toolCallId;

    /**
     * 工具名
     */
    private String name;

    /**
     * 工具展示名，取自意图树配置
     */
    private String displayName;

    /**
     * 入参列表（schema 声明序），卡片主视图展示用
     */
    private List<AgentConfirmField> fields;

    /**
     * 完整调用参数 JSON，卡片折叠区展示
     */
    private String arguments;
}
