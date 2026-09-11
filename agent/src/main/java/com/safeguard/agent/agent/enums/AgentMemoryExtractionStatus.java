package com.safeguard.agent.agent.enums;

/**
 * 抽取状态，哪几个值推进水位见 AgentMemoryExtractionMapper.selectWatermark
 */
public enum AgentMemoryExtractionStatus {

    /**
     * 在飞，部分唯一索引保证同会话只有一条
     */
    PROCESSING,

    /**
     * 判完有写入
     */
    WRITTEN,

    /**
     * 判完无产出，同样算处理过
     */
    NOOP,

    /**
     * 重试耗尽或容量拒收，坏抽取不许永久堵塞水位
     */
    DROPPED,

    /**
     * 提交期快照失配，本批作废重来，不计入尝试次数
     */
    CONFLICT
}
