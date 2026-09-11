package com.safeguard.agent.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 确认卡片上的一项入参
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentConfirmField {

    /**
     * 原始字段名
     */
    private String name;

    /**
     * 展示标签，取自 schema title，未声明时等于 name
     */
    private String label;

    /**
     * 模型填入的参数值
     */
    private String value;
}
