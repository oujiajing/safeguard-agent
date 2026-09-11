package com.safeguard.agent.agent.memory;

import java.util.List;

/**
 * 记忆视图快照：一次 agent call 内读一次，只管注入
 * 不带版本号——提交期双校验拿的是管道自己读的那份控制行，与模型看到的这份块无关
 */
public record AgentMemorySnapshot(List<AgentMemoryItem> items) {

    /**
     * 读库异常与长期记忆开关关闭共用这一个空值，调用方不必区分
     */
    public static AgentMemorySnapshot empty() {
        return new AgentMemorySnapshot(List.of());
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }
}
