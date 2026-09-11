package com.safeguard.agent.rag.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 智能体列表，附带当前执行架构供页面标注哪些槽位生效
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentProfileListVO {

    /**
     * 取自 safeguard.engine.type，部署级配置，页面只读展示
     */
    private String mode;

    /**
     * 当前架构下生效的槽位总数，全体智能体共用，作覆盖率分母
     */
    private Integer effectiveSlotTotal;

    private List<AgentProfileVO> agents;
}
