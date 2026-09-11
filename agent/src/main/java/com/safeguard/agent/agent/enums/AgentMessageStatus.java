package com.safeguard.agent.agent.enums;

/**
 * Agent 消息状态，与 rag 侧 MessageStatus 分立
 */
public enum AgentMessageStatus {

    /**
     * 正常完成
     */
    NORMAL,

    /**
     * 用户中断，内容为已生成的部分
     */
    INTERRUPTED,

    /**
     * 挂起在写操作确认上，唯一的非终态；用户点头或拒绝后续跑并改回 NORMAL
     */
    AWAITING_CONFIRM
}
