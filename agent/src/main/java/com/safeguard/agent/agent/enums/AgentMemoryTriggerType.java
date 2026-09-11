package com.safeguard.agent.agent.enums;

/**
 * 抽取的触发方，两条入口共用同一条管道，只在门槛与失败反馈上分叉
 */
public enum AgentMemoryTriggerType {

    /**
     * 模型调工具，同步等结果，待处理一条即执行
     */
    FLUSH,

    /**
     * 轮次释放后异步，待处理满门槛才执行
     */
    BACKGROUND
}
