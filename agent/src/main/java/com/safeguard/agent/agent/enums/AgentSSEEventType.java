package com.safeguard.agent.agent.enums;

import lombok.AllArgsConstructor;

/**
 * Agent 模式 SSE 事件协议，与 workflow 协议两套分立
 */
@AllArgsConstructor
public enum AgentSSEEventType {

    /**
     * 会话与任务元信息
     */
    META("meta"),

    /**
     * 增量消息（response / think）
     */
    MESSAGE("message"),

    /**
     * 工具进度 {name, displayName, status: start|end, result, ok}
     */
    TOOL("tool"),

    /**
     * 运行提示（如达到迭代上限的熔断预告），不落库
     */
    HINT("hint"),

    /**
     * 等待用户确认写操作 {messageId, title, calls}，与 finish 互斥
     */
    CONFIRM("confirm"),

    /**
     * 回复完成
     */
    FINISH("finish"),

    /**
     * 流结束
     */
    DONE("done"),

    /**
     * 用户取消
     */
    CANCEL("cancel");

    private final String value;

    public String value() {
        return value;
    }
}
